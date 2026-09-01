package warpscout

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

const (
	findJunkSample        = 3
	defaultJunkThreshold  = 95
	findJunkQuitHint      = "q or Ctrl+C to stop and keep the best set"
	findJunkPlainQuitHint = "Ctrl+C to stop and keep the best set"
)

type junkCandidate struct {
	jc, jmin, jmax int
	working, total int
	i1, i1Label    string
}

func (c junkCandidate) meets(pct int) bool {
	return c.total > 0 && c.working*100 >= c.total*pct
}

func (c junkCandidate) String() string {
	return fmt.Sprintf("jc=%d jmin=%d jmax=%d  %d/%d working", c.jc, c.jmin, c.jmax, c.working, c.total)
}

func runFindJunk(ctx context.Context, opts options, run protoRun, timeout time.Duration) error {
	ctx, stop := signal.NotifyContext(ctx, os.Interrupt)
	defer stop()
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	intro := fmt.Sprintf("Searching AmneziaWG junk params (%d addresses per subnet, %s).", opts.perSubnet, i1Note())
	if usePlainOutput(opts) {
		intro += " Press " + findJunkPlainQuitHint + "."
	}
	fmt.Fprintln(os.Stderr, errPal.dim(intro))
	fmt.Fprintln(os.Stderr)

	var best junkCandidate
	for attempt := 1; ctx.Err() == nil; attempt++ {
		genJunkParams()
		candidates := findJunkI1Candidates("")
		if opts.genI1 != "" {
			chain, label, err := genI1(opts.genI1, opts.i1Host)
			if err != nil {
				return err
			}
			candidates = []i1Candidate{{chain: chain, label: label}}
		}
		for i1Index, candidate := range candidates {
			awgI1, genI1Label = candidate.chain, candidate.label
			attemptLabel := fmt.Sprintf("Attempt %d.%d", attempt, i1Index+1)
			header := fmt.Sprintf(
				"%s: jc=%d jmin=%d jmax=%d, I1 %d/%d, %s",
				attemptLabel, awgJc, awgJmin, awgJmax, i1Index+1, len(candidates), i1Note(),
			)
			if usePlainOutput(opts) {
				fmt.Fprintln(os.Stderr, errPal.dim(header))
			}

			ph, err := runScanUI(ctx, cancel, opts, run, expandPools(opts.perSubnet), timeout, header, findJunkQuitHint)
			if ctx.Err() != nil {
				break
			}
			if err != nil {
				fmt.Fprintln(os.Stderr, errPal.fail(fmt.Sprintf("%s: %v", attemptLabel, err)))
				continue
			}

			c := scoreJunk(ph)
			summary := attemptLabel + ": " + c.String()
			if c.meets(opts.threshold) {
				fmt.Fprintln(os.Stderr, errPal.ok(summary))
			} else {
				fmt.Fprintln(os.Stderr, errPal.dim(summary))
			}
			fmt.Fprintln(os.Stderr)
			if c.working > best.working {
				best = c
			}
			if c.meets(opts.threshold) {
				return reportJunk(c, true)
			}
		}
		if ctx.Err() != nil {
			break
		}
	}

	if best.total == 0 {
		fmt.Fprintln(os.Stderr, errPal.dim("Stopped before any junk set finished a scan - nothing to report."))
		return nil
	}
	return reportJunk(best, false)
}

func scoreJunk(ph phaseResult) junkCandidate {
	c := junkCandidate{jc: awgJc, jmin: awgJmin, jmax: awgJmax, i1: awgI1, i1Label: genI1Label}
	for _, r := range ph.results {
		c.total++
		if r.working() {
			c.working++
		}
	}
	return c
}

func reportJunk(c junkCandidate, complete bool) error {
	fmt.Fprintln(os.Stderr)
	note := i1NoteFor(c.i1, c.i1Label)
	if complete {
		fmt.Fprintln(os.Stderr, errPal.ok(fmt.Sprintf("Good enough: %s (%s)", c, note)))
	} else {
		fmt.Fprintln(os.Stderr, errPal.dim(fmt.Sprintf("Stopped. Best set: %s (%s)", c, note)))
	}
	fmt.Fprintln(os.Stderr)
	fmt.Fprintln(os.Stderr, errPal.title("Ready-to-run scan command with these parameters:"))
	fmt.Fprintln(os.Stderr)
	outPal := palette{enabled: colorEnabled(os.Stdout)}
	fmt.Fprintln(os.Stdout, outPal.accent(junkCommand(c)))
	fmt.Fprintln(os.Stderr)
	return nil
}

func i1Note() string {
	return i1NoteFor(awgI1, genI1Label)
}

func i1NoteFor(i1, label string) string {
	if label != "" {
		return "generated " + label + " I1"
	}
	switch i1 {
	case "":
		return "no I1"
	case i1Default:
		return "default iCloud I1"
	}
	return "custom I1"
}

func runnableBinary() string {
	name := filepath.Base(os.Args[0])
	if runtime.GOOS != "windows" {
		return "./" + name
	}
	if !strings.HasSuffix(strings.ToLower(name), ".exe") {
		name += ".exe"
	}
	return name
}

func junkCommand(c junkCandidate) string {
	parts := []string{runnableBinary(), "scan", "-proto", protoAWG,
		fmt.Sprintf("-jc %d", c.jc), fmt.Sprintf("-jmin %d", c.jmin), fmt.Sprintf("-jmax %d", c.jmax)}
	if c.i1 == "" {
		parts = append(parts, "-i1 none")
	} else if c.i1 != i1Default {
		parts = append(parts, fmt.Sprintf("-i1 %q", c.i1))
	}
	return strings.Join(parts, " ")
}
