package warpscout

import (
	"context"
	"fmt"
	"net/netip"
	"os"
	"os/signal"
	"strings"
	"time"
)

// A working SNI still leaves endpoints down - measured 11-13 of 14 ports per
// address at masqueDefaultAttempts - so find-junk's 95 would reject every
// candidate here.
const defaultSNIThreshold = 70

// One address per subnet keeps a round at the same size on either transport:
// two addresses times masqueEndpointPorts. The QUIC pools are single addresses
// anyway, but the HTTP/2 ones are /24s, and sampling them fully would drown the
// threshold in addresses the SNI has nothing to do with.
const findSNISample = 1

const (
	findSNIQuitHint      = "q or Ctrl+C to stop and keep the best SNI"
	findSNIPlainQuitHint = "Ctrl+C to stop and keep the best SNI"
)

// The default first, so an unfiltered network is done after one round. The rest
// are the hosts the generated I1 already mimics - same job, same measured set.
var sniCandidates = append([]string{masqueDefaultSNI}, i1Hosts...)

type sniCandidate struct {
	sni            string
	working, total int
}

func (c sniCandidate) meets(pct int) bool {
	return c.total > 0 && c.working*100 >= c.total*pct
}

func (c sniCandidate) String() string {
	return fmt.Sprintf("%s  %d/%d working", c.sni, c.working, c.total)
}

func runFindSNI(ctx context.Context, opts options, run protoRun, ips []netip.Addr, timeout time.Duration) error {
	ctx, stop := signal.NotifyContext(ctx, os.Interrupt)
	defer stop()
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	intro := fmt.Sprintf("Trying %d SNIs against the MASQUE endpoints.", len(sniCandidates))
	if usePlainOutput(opts) {
		intro += " Press " + findSNIPlainQuitHint + "."
	}
	fmt.Fprintln(os.Stderr, errPal.dim(intro))
	fmt.Fprintln(os.Stderr)

	var best sniCandidate
	for i, sni := range sniCandidates {
		if ctx.Err() != nil {
			break
		}
		masqueSNI = sni
		header := fmt.Sprintf("SNI %d/%d: %s", i+1, len(sniCandidates), sni)
		if usePlainOutput(opts) {
			fmt.Fprintln(os.Stderr, errPal.dim(header))
		}

		ph, err := runScanUI(ctx, cancel, opts, run, ips, timeout, header, findSNIQuitHint)
		if ctx.Err() != nil {
			break
		}
		if err != nil {
			fmt.Fprintln(os.Stderr, errPal.fail(fmt.Sprintf("%s: %v", sni, err)))
			continue
		}

		c := scoreSNI(sni, ph)
		if c.meets(opts.threshold) {
			fmt.Fprintln(os.Stderr, errPal.ok(c.String()))
			fmt.Fprintln(os.Stderr)
			return reportSNI(run, c, true)
		}
		fmt.Fprintln(os.Stderr, errPal.dim(c.String()))
		fmt.Fprintln(os.Stderr)
		if c.working > best.working {
			best = c
		}
	}

	if best.total == 0 {
		fmt.Fprintln(os.Stderr, errPal.dim("No SNI finished a scan - nothing to report."))
		return nil
	}
	return reportSNI(run, best, false)
}

func scoreSNI(sni string, ph phaseResult) sniCandidate {
	c := sniCandidate{sni: sni}
	for _, r := range ph.results {
		c.total++
		if r.working() {
			c.working++
		}
	}
	return c
}

func reportSNI(run protoRun, c sniCandidate, complete bool) error {
	fmt.Fprintln(os.Stderr)
	if complete {
		fmt.Fprintln(os.Stderr, errPal.ok("Good enough: "+c.String()))
	} else {
		fmt.Fprintln(os.Stderr, errPal.dim("Best SNI so far: "+c.String()))
	}
	fmt.Fprintln(os.Stderr)
	fmt.Fprintln(os.Stderr, errPal.title("Ready-to-run scan command with this SNI:"))
	fmt.Fprintln(os.Stderr)
	outPal := palette{enabled: colorEnabled(os.Stdout)}
	fmt.Fprintln(os.Stdout, outPal.accent(sniCommand(run, c)))
	fmt.Fprintln(os.Stderr)
	return nil
}

func sniCommand(run protoRun, c sniCandidate) string {
	parts := []string{runnableBinary(), "scan", "-proto", run.name}
	if c.sni != masqueDefaultSNI {
		parts = append(parts, "-masque-sni", c.sni)
	}
	return strings.Join(parts, " ")
}
