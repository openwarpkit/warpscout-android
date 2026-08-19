//go:build linux

package warpscout

import (
	"syscall"

	"golang.org/x/sys/unix"
)

const deviceBindSupported = true

// SO_BINDTODEVICE needs CAP_NET_RAW. A positive maxseg clamps TCP MSS: a tun
// that advertises a large MTU but really egresses over a smaller internet path
// PMTU-blackholes big TLS records, so registration over such an interface must
// cap the segment size.
func deviceControl(iface string, maxseg int) func(network, address string, c syscall.RawConn) error {
	return func(_, _ string, c syscall.RawConn) error {
		var opErr error
		if err := c.Control(func(fd uintptr) {
			if opErr = unix.SetsockoptString(int(fd), unix.SOL_SOCKET, unix.SO_BINDTODEVICE, iface); opErr != nil {
				return
			}
			if maxseg > 0 {
				opErr = unix.SetsockoptInt(int(fd), unix.IPPROTO_TCP, unix.TCP_MAXSEG, maxseg)
			}
		}); err != nil {
			return err
		}
		return opErr
	}
}
