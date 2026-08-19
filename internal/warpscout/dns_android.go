//go:build android

package warpscout

import (
	"context"
	"net"
	"os"
	"path/filepath"
	"strings"
)

// Android ships no /etc/resolv.conf, so Go's own resolver falls back to
// 127.0.0.1:53 and every lookup fails "connection refused" - which is every
// command, since even -relay has a hostname. Termux keeps its own copy under
// $PREFIX/etc, so honour that and fall back to the WARP resolver.
const androidFallbackDNS = "1.1.1.1:53"

func init() {
	server := termuxNameserver()
	net.DefaultResolver.Dial = func(ctx context.Context, network, _ string) (net.Conn, error) {
		return (&net.Dialer{}).DialContext(ctx, network, server)
	}
}

func termuxNameserver() string {
	prefix := os.Getenv("PREFIX")
	if prefix == "" {
		return androidFallbackDNS
	}
	data, err := os.ReadFile(filepath.Join(prefix, "etc", "resolv.conf"))
	if err != nil {
		return androidFallbackDNS
	}
	for _, line := range strings.Split(string(data), "\n") {
		fields := strings.Fields(line)
		if len(fields) >= 2 && fields[0] == "nameserver" {
			return net.JoinHostPort(fields[1], "53")
		}
	}
	return androidFallbackDNS
}
