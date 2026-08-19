package warpscout

import (
	"encoding/binary"
	"fmt"
	"sort"
	"strings"
)

const i1ProfileRandom = "random"

// Protocols AmneziaWG's own guide recommends for mimicry: high-volume UDP that
// every network already passes.
var i1Generators = map[string]func(host string) (string, error){
	"quic": func(host string) (string, error) {
		chain, _, err := genQUICI1(host)
		return chain, err
	},
	"dns":  genDNSI1,
	"sip":  genSIPI1,
	"stun": genSTUNI1,
}

var i1Hosts = []string{
	"www.apple.com",
	"www.google.com",
	"www.microsoft.com",
	"cdn.jsdelivr.net",
}

var genI1Label string

func genI1(profile, host string) (chain, label string, err error) {
	if profile == i1ProfileRandom {
		names := i1Profiles()
		profile = names[randRange(0, len(names)-1)]
	}
	gen, ok := i1Generators[profile]
	if !ok {
		return "", "", fmt.Errorf("unknown I1 profile %q (want %s)", profile, strings.Join(i1Profiles(), "|"))
	}
	if host == "" {
		host = i1Hosts[randRange(0, len(i1Hosts)-1)]
	}
	chain, err = gen(host)
	if err != nil {
		return "", "", err
	}
	return chain, fmt.Sprintf("%s(%s)", profile, host), nil
}

func regenI1(o options) error {
	chain, label, err := genI1(o.genI1, o.i1Host)
	if err != nil {
		return err
	}
	awgI1, genI1Label = chain, label
	return nil
}

func i1Profiles() []string {
	names := make([]string, 0, len(i1Generators))
	for name := range i1Generators {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

// Standard A query plus an EDNS0 OPT record whose padding option (RFC 7830)
// carries the random bytes, so the packet stays a well-formed query.
func genDNSI1(host string) (string, error) {
	qname, err := dnsName(host)
	if err != nil {
		return "", err
	}
	question := append(qname, 0, 1, 0, 1)

	padLen := randRange(dnsPadMin, dnsPadMax)
	opt := []byte{0}                               // root name
	opt = binary.BigEndian.AppendUint16(opt, 41)   // OPT
	opt = binary.BigEndian.AppendUint16(opt, 4096) // UDP payload size
	opt = append(opt, 0, 0, 0, 0)                  // extended rcode and flags
	opt = binary.BigEndian.AppendUint16(opt, uint16(padLen+4))
	opt = binary.BigEndian.AppendUint16(opt, 12) // padding option
	opt = binary.BigEndian.AppendUint16(opt, uint16(padLen))

	c := &cpsChain{}
	c.tag("r", 2) // transaction id
	c.bytes([]byte{0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 1})
	c.bytes(question)
	c.bytes(opt)
	c.tag("r", padLen)
	return c.String(), nil
}

func dnsName(host string) ([]byte, error) {
	var out []byte
	for _, label := range strings.Split(strings.Trim(host, "."), ".") {
		if label == "" || len(label) > dnsLabelMax {
			return nil, fmt.Errorf("bad DNS label %q in %q", label, host)
		}
		out = append(out, byte(len(label)))
		out = append(out, label...)
	}
	return append(out, 0), nil
}

func genSIPI1(host string) (string, error) {
	c := &cpsChain{}
	c.text("OPTIONS sip:" + host + " SIP/2.0\r\n")
	c.text(fmt.Sprintf("Via: SIP/2.0/UDP 192.168.%d.%d:5060;branch=z9hG4bK", randRange(0, 255), randRange(2, 254)))
	c.tag("rc", 10)
	c.text("\r\nFrom: <sip:")
	c.tag("rc", 8)
	c.text("@" + host + ">;tag=")
	c.tag("rd", 6)
	c.text("\r\nTo: <sip:" + host + ">\r\nCall-ID: ")
	c.tag("rc", 16)
	c.text("@" + host + "\r\nCSeq: ")
	c.tag("rd", 4)
	c.text(" OPTIONS\r\nMax-Forwards: 70\r\nUser-Agent: PJSIP/2.13\r\nContent-Length: 0\r\n\r\n")
	return c.String(), nil
}

func genSTUNI1(host string) (string, error) {
	softwareLen := randRange(stunSoftwareMin/4, stunSoftwareMax/4) * 4
	attrLen := 4 + softwareLen

	c := &cpsChain{}
	header := binary.BigEndian.AppendUint16(nil, 0x0001) // Binding Request
	header = binary.BigEndian.AppendUint16(header, uint16(attrLen))
	header = binary.BigEndian.AppendUint32(header, 0x2112A442) // magic cookie
	c.bytes(header)
	c.tag("r", 12) // transaction id
	software := binary.BigEndian.AppendUint16(nil, 0x8022)
	software = binary.BigEndian.AppendUint16(software, uint16(softwareLen))
	c.bytes(software)
	c.tag("rc", softwareLen)
	return c.String(), nil
}

const (
	dnsLabelMax     = 63
	dnsPadMin       = 60
	dnsPadMax       = 180
	stunSoftwareMin = 16
	stunSoftwareMax = 32
)

type cpsChain struct {
	sb strings.Builder
}

func (c *cpsChain) bytes(b []byte) {
	if len(b) > 0 {
		fmt.Fprintf(&c.sb, "<b 0x%x>", b)
	}
}

func (c *cpsChain) text(s string) {
	c.bytes([]byte(s))
}

func (c *cpsChain) tag(name string, n int) {
	fmt.Fprintf(&c.sb, "<%s %d>", name, n)
}

func (c *cpsChain) String() string {
	return c.sb.String()
}
