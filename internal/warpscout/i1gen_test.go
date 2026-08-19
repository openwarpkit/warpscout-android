package warpscout

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"encoding/hex"
	"regexp"
	"strconv"
	"strings"
	"testing"
)

const testI1Host = "www.example.com"

// The whole point of the QUIC profile: a DPI that derives the Initial keys from
// the DCID must be able to decrypt the packet and find a real ClientHello.
func TestGenQUICI1Decrypts(t *testing.T) {
	chain, packet, err := genQUICI1(testI1Host)
	if err != nil {
		t.Fatal(err)
	}

	dcidLen := int(packet[5])
	dcid := packet[6 : 6+dcidLen]
	off := 6 + dcidLen
	off += 1 + int(packet[off]) // SCID
	_, off = readVarint(t, packet, off)
	_, pnOffset := readVarint(t, packet, off)

	key, iv, hp := quicInitialKeys(dcid)
	mask, err := quicHeaderMask(hp, packet[pnOffset+4:pnOffset+quicSampleEnd])
	if err != nil {
		t.Fatal(err)
	}

	header := append([]byte(nil), packet[:pnOffset]...)
	header[0] ^= mask[0] & 0x0f
	pknLen := int(header[0]&0x03) + 1
	for i := range pknLen {
		header = append(header, packet[pnOffset+i]^mask[1+i])
		iv[len(iv)-pknLen+i] ^= header[pnOffset+i]
	}

	block, err := aes.NewCipher(key)
	if err != nil {
		t.Fatal(err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		t.Fatal(err)
	}
	plaintext, err := gcm.Open(nil, iv, packet[pnOffset+pknLen:], header)
	if err != nil {
		t.Fatalf("Initial packet does not decrypt: %v", err)
	}

	if plaintext[0] != 0x06 {
		t.Errorf("payload starts with 0x%02x, want a CRYPTO frame (0x06)", plaintext[0])
	}
	if !bytes.Contains(plaintext, []byte{0x01}) || !bytes.Contains(plaintext, []byte(testI1Host)) {
		t.Errorf("ClientHello for %q not found in payload", testI1Host)
	}
	if got := cpsLen(t, chain); got != len(packet) {
		t.Errorf("CPS chain covers %d bytes, packet is %d", got, len(packet))
	}
}

func TestGenI1Profiles(t *testing.T) {
	for _, profile := range i1Profiles() {
		chain, label, err := genI1(profile, testI1Host)
		if err != nil {
			t.Fatalf("%s: %v", profile, err)
		}
		if !strings.HasPrefix(label, profile+"(") {
			t.Errorf("%s: label = %q", profile, label)
		}
		if n := cpsLen(t, chain); n < 40 || n > tunnelMTU {
			t.Errorf("%s: packet is %d bytes, want 40..%d", profile, n, tunnelMTU)
		}
	}
}

func TestGenI1UnknownProfile(t *testing.T) {
	if _, _, err := genI1("carrier-pigeon", testI1Host); err == nil {
		t.Error("unknown profile must be rejected")
	}
}

func readVarint(t *testing.T, b []byte, off int) (value, next int) {
	t.Helper()
	size := 1 << (b[off] >> 6)
	value = int(b[off] & 0x3f)
	for _, x := range b[off+1 : off+size] {
		value = value<<8 | int(x)
	}
	return value, off + size
}

var cpsTag = regexp.MustCompile(`^<(b) 0x([0-9a-f]+)>|^<(r|rc|rd) (\d+)>`)

// Size of the packet a CPS chain builds, and a check that amneziawg-go can
// parse every tag in it (unknown tags or odd hex make IpcSet fail).
func cpsLen(t *testing.T, chain string) int {
	t.Helper()
	total := 0
	for rest := chain; rest != ""; {
		m := cpsTag.FindStringSubmatch(rest)
		if m == nil {
			t.Fatalf("unparsable CPS at %q", rest)
		}
		if m[1] == "b" {
			raw, err := hex.DecodeString(m[2])
			if err != nil {
				t.Fatalf("bad hex %q: %v", m[2], err)
			}
			total += len(raw)
		} else {
			n, _ := strconv.Atoi(m[4])
			total += n
		}
		rest = rest[len(m[0]):]
	}
	return total
}
