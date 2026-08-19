package warpscout

import (
	"fmt"
	"math/rand"
	"net/netip"
	"strings"
)

var poolsV4 = []netip.Prefix{
	netip.MustParsePrefix("8.6.112.0/24"),
	netip.MustParsePrefix("8.34.70.0/24"),
	netip.MustParsePrefix("8.34.146.0/24"),
	netip.MustParsePrefix("8.35.211.0/24"),
	netip.MustParsePrefix("8.39.125.0/24"),
	netip.MustParsePrefix("8.39.204.0/24"),
	netip.MustParsePrefix("8.39.214.0/24"),
	netip.MustParsePrefix("8.47.69.0/24"),
	netip.MustParsePrefix("162.159.192.0/24"),
	netip.MustParsePrefix("162.159.195.0/24"),
	netip.MustParsePrefix("188.114.96.0/24"),
	netip.MustParsePrefix("188.114.97.0/24"),
	netip.MustParsePrefix("188.114.98.0/24"),
	netip.MustParsePrefix("188.114.99.0/24"),
}

var poolsV6 = []netip.Prefix{
	netip.MustParsePrefix("2606:4700:d0::/48"),
	netip.MustParsePrefix("2606:4700:d1::/48"),
}

var pools = poolsV4

func poolsFor(run protoRun, ipv6 bool) []netip.Prefix {
	if run.isMASQUE() {
		if ipv6 {
			if run.isH2() {
				return masqueH2PoolsV6
			}
			return masquePoolsV6
		}
		if run.isH2() {
			return masqueH2PoolsV4
		}
		return masquePoolsV4
	}
	if ipv6 {
		return poolsV6
	}
	return poolsV4
}

// Anything wider than /20 is what the built-in pools are for, and expandV4
// would enumerate the whole range.
const targetMinBitsV4 = 20

func parseTargets(spec string) ([]netip.Prefix, error) {
	var out []netip.Prefix
	for _, field := range strings.Split(spec, ",") {
		field = strings.TrimSpace(field)
		if field == "" {
			continue
		}
		p, err := parseTarget(field)
		if err != nil {
			return nil, err
		}
		if len(out) > 0 && out[0].Addr().Is4() != p.Addr().Is4() {
			return nil, fmt.Errorf("-target mixes IPv4 and IPv6: scan one family per run")
		}
		out = append(out, p)
	}
	if len(out) == 0 {
		return nil, fmt.Errorf("-target is empty")
	}
	return out, nil
}

func parseTarget(field string) (netip.Prefix, error) {
	if a, err := netip.ParseAddr(field); err == nil {
		return netip.PrefixFrom(a.Unmap(), a.Unmap().BitLen()), nil
	}
	p, err := netip.ParsePrefix(field)
	if err != nil {
		return netip.Prefix{}, fmt.Errorf("-target %q is neither an IP address nor a CIDR prefix", field)
	}
	if p.Addr().Is4() && p.Bits() < targetMinBitsV4 {
		return netip.Prefix{}, fmt.Errorf("-target %s is too wide: use /%d or narrower", field, targetMinBitsV4)
	}
	return p.Masked(), nil
}

func expandPools(perSubnet int) []netip.Addr {
	var ips []netip.Addr
	for _, p := range pools {
		if p.Addr().Is4() {
			ips = append(ips, expandV4(p, perSubnet)...)
			continue
		}
		ips = append(ips, expandV6(p, perSubnet)...)
	}
	return ips
}

func expandV4(p netip.Prefix, perSubnet int) []netip.Addr {
	p = p.Masked()
	var ips []netip.Addr
	for a := p.Addr(); p.Contains(a); a = a.Next() {
		ips = append(ips, a)
	}
	if perSubnet <= 0 || perSubnet >= len(ips) {
		return ips
	}
	rand.Shuffle(len(ips), func(i, j int) { ips[i], ips[j] = ips[j], ips[i] })
	return ips[:perSubnet]
}

func expandV6(p netip.Prefix, perSubnet int) []netip.Addr {
	count := perSubnet
	if count <= 0 || count >= 256 {
		count = 256
	}
	base := p.Masked().Addr().As16()
	// Random bytes below the prefix, mirroring upstream endpoint6(): hextet 4
	// stays 0 for the /48 pools.
	start := max(8, (p.Bits()+7)/8)
	if start == 16 {
		return []netip.Addr{p.Addr()}
	}
	ips := make([]netip.Addr, 0, count)
	for i := 0; i < count; i++ {
		a := base
		for b := start; b < 16; b++ {
			a[b] = byte(rand.Intn(256))
		}
		ips = append(ips, netip.AddrFrom16(a))
	}
	return ips
}
