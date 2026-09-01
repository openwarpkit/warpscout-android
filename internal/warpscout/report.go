package warpscout

import (
	"fmt"
	"io"
	"net/netip"
	"os"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/charmbracelet/lipgloss"
	"github.com/charmbracelet/lipgloss/table"
)

var (
	dimColor    = lipgloss.Color("240")
	okColor     = lipgloss.Color("42")
	failColor   = lipgloss.Color("196")
	warnColor   = lipgloss.Color("214")
	accentColor = lipgloss.Color("39")
)

type conStyles struct {
	title, dim, ok, fail, warn, accent, box, cell lipgloss.Style
}

func newConStyles(r *lipgloss.Renderer) conStyles {
	return conStyles{
		title:  r.NewStyle().Bold(true),
		dim:    r.NewStyle().Foreground(dimColor),
		ok:     r.NewStyle().Foreground(okColor),
		fail:   r.NewStyle().Foreground(failColor),
		warn:   r.NewStyle().Foreground(warnColor),
		accent: r.NewStyle().Foreground(accentColor),
		box:    r.NewStyle().Border(lipgloss.RoundedBorder()).BorderForeground(dimColor).Padding(0, 1),
		cell:   r.NewStyle().Padding(0, 1),
	}
}

const projectURL = "https://github.com/vernette/warpscout"

const (
	statusOK = iota
	statusTorn
	statusNone
)

type endpointResult struct {
	ip       netip.Addr
	endpoint string
	exit     metaResult
	epPing   time.Duration // host ICMP ping to the bare IP, no tunnel involved
	tunPing  time.Duration // in-tunnel RTT to pingTarget, valid only when measured
	loss     float32       // in-tunnel packet loss 0..1, valid only when measured
	speed    float64       // in-tunnel download Mbit/s (-speed), 0 when not measured
	measured bool          // tunPing/loss were sampled (-tun-ping)
	ok       bool
	durable  bool
}

func (r endpointResult) sortPing() time.Duration {
	if r.measured {
		return r.tunPing
	}
	return r.epPing
}

func (r endpointResult) epPingStr() string  { return latencyStr(r.epPing) }
func (r endpointResult) tunPingStr() string { return latencyStr(r.tunPing) }

func (r endpointResult) lossStr() string {
	if !r.measured {
		return "-"
	}
	return fmt.Sprintf("%.0f%%", r.loss*100)
}

func tunFields(tunPing, loss string, ping bool) string {
	if !ping {
		return ""
	}
	return fmt.Sprintf("%-9s %-6s ", tunPing, loss)
}

func tunFieldsOf(r endpointResult, ping bool) string {
	return tunFields(r.tunPingStr(), r.lossStr(), ping)
}

func anySpeed(results []endpointResult) bool {
	for _, r := range results {
		if r.speed > 0 {
			return true
		}
	}
	return false
}

func speedStr(mbps float64) string {
	if mbps <= 0 {
		return "-"
	}
	return fmt.Sprintf("%.1f Mbps", mbps)
}

func speedField(s string, show bool) string {
	if !show {
		return ""
	}
	return fmt.Sprintf("%-11s ", s)
}

func speedCells(r endpointResult, show bool) []string {
	if !show {
		return nil
	}
	return []string{speedStr(r.speed)}
}

func speedHeaders(show bool) []string {
	if !show {
		return nil
	}
	return []string{"SPEED"}
}

func sortNote(ping bool) string {
	if bestBy == bestKeySpeed {
		return "sorted by download speed"
	}
	if ping {
		return "sorted by in-tunnel loss, then in-tunnel ping"
	}
	return "sorted by ping to the endpoint"
}

func bestNote(ping bool) string {
	if bestBy == bestKeySpeed {
		return "highest download speed"
	}
	if ping {
		return "lowest in-tunnel loss, then in-tunnel ping"
	}
	return "lowest ping to the endpoint"
}

func latencyStr(d time.Duration) string {
	if d <= 0 {
		return "?"
	}
	if d < time.Millisecond {
		return fmt.Sprintf("%.2fms", float64(d)/float64(time.Millisecond))
	}
	return fmt.Sprintf("%dms", d.Milliseconds())
}

func (r endpointResult) working() bool  { return r.ok && r.durable }
func (r endpointResult) tornDown() bool { return r.ok && !r.durable }

func noFlag(string) string { return "" }

func withFlag(flag, code string) string {
	if flag == "" {
		return code
	}
	return flag + " " + code
}

func exitRegion(t metaResult) string {
	if t.loc == "" {
		return "?"
	}
	return withFlag(flagEmoji(t.loc), t.loc)
}

func exitColo(t metaResult) string {
	if t.colo == "" {
		return "?"
	}
	return t.colo
}

func exitColoLocation(t metaResult) string { return coloLocation(t) }

// An empty row is only worth showing when the scan really covered the whole
// subnet, so subnets a filter emptied are dropped.
func poolsWithHits(ph phaseResult) []netip.Prefix {
	out := make([]netip.Prefix, 0, len(pools))
	for _, p := range pools {
		if len(subnetEndpoints(ph.results, p)) > 0 {
			out = append(out, p)
		}
	}
	return out
}

// want=false inverts the filter, which is all -exclude-node/-exclude-country
// are. An endpoint with no colo at all is dropped by the positive filter and
// kept by the negative one.
func filterByColo(ph phaseResult, colos []string, want bool) phaseResult {
	listed := upperSet(colos)
	return filterResults(ph, func(r endpointResult) bool {
		_, ok := listed[strings.ToUpper(r.exit.colo)]
		return ok == want
	})
}

func filterByCountry(ph phaseResult, countries []string, want bool) phaseResult {
	listed := upperSet(countries)
	return filterResults(ph, func(r endpointResult) bool {
		if r.exit.coloISO == "" {
			return !want
		}
		_, ok := listed[strings.ToUpper(r.exit.coloISO)]
		return ok == want
	})
}

func upperSet(vals []string) map[string]struct{} {
	set := make(map[string]struct{}, len(vals))
	for _, v := range vals {
		set[strings.ToUpper(v)] = struct{}{}
	}
	return set
}

func filterResults(ph phaseResult, keep func(endpointResult) bool) phaseResult {
	var kept []endpointResult
	for _, r := range ph.results {
		if keep(r) {
			kept = append(kept, r)
		}
	}
	return phaseResult{ph.run, kept}
}

func workingSorted(results []endpointResult) []endpointResult {
	return filterSorted(results, endpointResult.working)
}

func tornSorted(results []endpointResult) []endpointResult {
	return filterSorted(results, endpointResult.tornDown)
}

func filterSorted(results []endpointResult, keep func(endpointResult) bool) []endpointResult {
	out := make([]endpointResult, 0, len(results))
	for _, r := range results {
		if keep(r) {
			out = append(out, r)
		}
	}
	sort.Slice(out, func(i, j int) bool { return lessByLossRTT(out[i], out[j]) })
	return out
}

const (
	bestKeyPing  = "ping"
	bestKeySpeed = "speed"
)

var bestKeys = []string{bestKeyPing, bestKeySpeed}

var bestBy = bestKeyPing

// Loss before ping, mirroring CloudflareWarpSpeedTest. Unmeasured endpoints
// carry loss 0, so without -tun-ping this degrades to ping-only ordering.
func lessByLossRTT(a, b endpointResult) bool {
	if bestBy == bestKeySpeed && a.speed != b.speed {
		return a.speed > b.speed // unmeasured is 0, so it sinks
	}
	if a.loss != b.loss {
		return a.loss < b.loss
	}
	if a.sortPing() != b.sortPing() {
		return lessDur(a.sortPing(), b.sortPing())
	}
	return a.endpoint < b.endpoint
}

func lessLossDur(la float32, da time.Duration, lb float32, db time.Duration) bool {
	if la != lb {
		return la < lb
	}
	return lessDur(da, db)
}

func lessDur(a, b time.Duration) bool {
	if (a <= 0) != (b <= 0) {
		return a > 0
	}
	return a < b
}

func bestByPing(picks []endpointResult) endpointResult {
	best := picks[0]
	for _, r := range picks[1:] {
		if lessByLossRTT(r, best) {
			best = r
		}
	}
	return best
}

const (
	reportRowFmt   = "%-22s %-13s %s%s%-10s %-6s %s\n"
	nodePickRowFmt = "%-6s %-22s %-13s %s%s%-10s %s\n"
)

func writeHeader(w io.Writer, working, probed int, ping, speed bool) {
	fmt.Fprintf(w, "# WARP endpoints: %d working / %d probed\n", working, probed)
	fmt.Fprintf(w, "# %s\n", sortNote(ping))
	if outer != nil {
		fmt.Fprintf(w, "# Scanned from inside a tunnel to %s - every endpoint here exits through that node's region\n", outer.label)
		fmt.Fprintln(w, "# ENDPOINT PING = ICMP from inside that tunnel to the endpoint, so it carries the outer hop too")
	} else {
		fmt.Fprintln(w, "# ENDPOINT PING = ICMP ping to the endpoint address from this host, no tunnel involved")
	}
	if ping {
		fmt.Fprintf(w, "# TUN PING / LOSS = RTT and packet loss measured inside the tunnel, to %s\n", pingTargetLabel())
	}
	if speed {
		note := "the ordering does not depend on it"
		if bestBy == bestKeySpeed {
			note = "only the endpoints the tables pick are measured, so the rest sort last"
		}
		fmt.Fprintf(w, "# SPEED = download throughput measured inside the tunnel; %s\n", note)
	}
	fmt.Fprintln(w, "# SEEN AS = region external services see through the tunnel")
	fmt.Fprintln(w, "# NODE / NODE LOCATION = Cloudflare WARP edge node the tunnel landed on, and where it sits")
	fmt.Fprintln(w, "# One /24 pool can land on several different nodes - NODE is per endpoint, not per subnet")
}

func writeFullReport(w io.Writer, results []endpointResult, ping bool) {
	working := workingSorted(results)
	torn := tornSorted(results)
	speed := anySpeed(results)
	writeHeader(w, len(working), len(results), ping, speed)
	if len(working) == 0 && len(torn) == 0 {
		fmt.Fprintln(w, "\nNo working endpoints found.")
		return
	}

	if len(working) > 0 {
		fmt.Fprintln(w)
		writeRows(w, working, ping, speed)
	}
	if len(torn) > 0 {
		fmt.Fprintf(w, "\n# %d torn down (handshake ok, data flowed, then cut and never recovered)\n", len(torn))
		writeRows(w, torn, ping, speed)
	}
	if len(working) > 0 {
		writeNodePicks(w, working, ping, speed)
	}
}

func writeRows(w io.Writer, results []endpointResult, ping, speed bool) {
	fmt.Fprintf(w, reportRowFmt, "ENDPOINT", "ENDPOINT PING", tunFields("TUN PING", "LOSS", ping), speedField("SPEED", speed), "SEEN AS", "NODE", "NODE LOCATION")
	for _, r := range results {
		fmt.Fprintf(w, reportRowFmt, r.endpoint, r.epPingStr(), tunFieldsOf(r, ping), speedField(speedStr(r.speed), speed), exitRegion(r.exit), exitColo(r.exit), exitColoLocation(r.exit))
	}
}

func subnetEndpoints(working []endpointResult, p netip.Prefix) []endpointResult {
	var picks []endpointResult
	for _, r := range working {
		if p.Contains(r.ip) {
			picks = append(picks, r)
		}
	}
	return picks
}

func writeConsole(w io.Writer, ph phaseResult, r *lipgloss.Renderer, ping bool) {
	st := newConStyles(r)
	results := ph.results
	working := workingSorted(results)
	torn := tornSorted(results)
	fmt.Fprintln(w)
	if len(working) == 0 {
		fmt.Fprintln(w, st.fail.Render("No working endpoints found."))
		writeTornNote(w, st, len(torn))
		return
	}

	fmt.Fprintln(w, banner(st))
	fmt.Fprintln(w)
	fmt.Fprintf(w, "Proto:     %s\n", st.accent.Render(protoLine(ph.run)))
	writeJunkNote(w, st, ph.run)
	fmt.Fprintf(w, "Nodes:     %s\n", st.accent.Render(uniqueSorted(working, func(r endpointResult) string { return r.exit.colo }, noFlag)))
	fmt.Fprintf(w, "Seen as:   %s\n", st.accent.Render(uniqueSorted(working, func(r endpointResult) string { return r.exit.loc }, flagEmoji)))
	if ping {
		fmt.Fprintf(w, "Ping to:   %s\n", st.accent.Render(pingTargetLabel()))
	}
	fmt.Fprintf(w, "Working:   %s\n", st.ok.Render(strconv.Itoa(len(working)))+st.dim.Render(" / ")+strconv.Itoa(len(results))+" probed")
	writeTornNote(w, st, len(torn))
	writePicksTable(w, st, working, torn, ping)
}

func protoLine(run protoRun) string {
	name := strings.ToUpper(run.name)
	if outer == nil {
		return name
	}
	return fmt.Sprintf("%s via %s (%s)", name, strings.ToUpper(outer.run.name), outer.endpoint)
}

func writeJunkNote(w io.Writer, st conStyles, run protoRun) {
	if outer != nil && outer.run.isAWG() {
		run = outer.run
	}
	if !run.isAWG() {
		return
	}
	fmt.Fprintf(w, "Junk:      %s %s\n",
		st.accent.Render(fmt.Sprintf("jc=%d jmin=%d jmax=%d", awgJc, awgJmin, awgJmax)),
		st.dim.Render("("+i1Note()+")"))
}

func writeTornNote(w io.Writer, st conStyles, n int) {
	if n == 0 {
		return
	}
	fmt.Fprintf(w, "%-11s%s\n", "Torn down:", st.warn.Render(fmt.Sprintf("%d (handshake ok, then cut mid-stream)", n)))
}

func banner(st conStyles) string {
	heart := "<3"
	if showEmoji {
		// U+2764 with VS16 measures as one cell but renders as two, so the box
		// misaligns - a U+1F49x heart is measured and rendered the same width.
		heart = "💕"
	}
	credit := st.dim.Render("Made with ") + st.fail.Render(heart) + st.dim.Render(" by vernette")
	return st.box.Render(lipgloss.JoinVertical(lipgloss.Center,
		st.title.Render("WARPSCOUT"),
		credit,
		st.dim.Render(projectURL),
	))
}

type pickRow struct {
	cells   []string
	status  int
	loss    float32
	latency time.Duration
	speed   float64
}

func tunCells(r endpointResult, ping bool) []string {
	if !ping {
		return nil
	}
	return []string{r.tunPingStr(), r.lossStr()}
}

func tunHeaders(ping bool) []string {
	if !ping {
		return nil
	}
	return []string{"TUN PING", "LOSS"}
}

func metricCols(first, n int) map[int]bool {
	cols := make(map[int]bool, n)
	for i := 0; i < n; i++ {
		cols[first+i] = true
	}
	return cols
}

func writePicksTable(w io.Writer, st conStyles, working, torn []endpointResult, ping bool) {
	speed := anySpeed(working) || anySpeed(torn)
	metrics := func(r endpointResult) []string {
		return append(tunCells(r, ping), speedCells(r, speed)...)
	}

	var rows []pickRow
	for _, p := range pools {
		subnet := p.String()
		if picks := subnetEndpoints(working, p); len(picks) > 0 {
			for _, r := range nodePicks(picks) {
				cells := append([]string{subnet, r.endpoint, r.epPingStr()}, metrics(r)...)
				cells = append(cells, exitRegion(r.exit), exitColo(r.exit), exitColoLocation(r.exit))
				rows = append(rows, pickRow{cells, statusOK, r.loss, r.sortPing(), r.speed})
			}
			continue
		}
		if picks := subnetEndpoints(torn, p); len(picks) > 0 {
			r := bestByPing(picks)
			cells := append([]string{subnet, r.endpoint, r.epPingStr()}, metrics(r)...)
			cells = append(cells, "torn down", "", "")
			rows = append(rows, pickRow{cells, statusTorn, r.loss, r.sortPing(), r.speed})
			continue
		}
		cells := []string{subnet, "no working endpoints", ""}
		cells = append(cells, make([]string, len(tunHeaders(ping))+len(speedHeaders(speed)))...)
		cells = append(cells, "", "", "")
		rows = append(rows, pickRow{cells, statusNone, 0, 0, 0})
	}
	// Status first: a subnet with no result carries a synthetic 0% loss, which
	// would otherwise sort it above a working endpoint that measured any loss.
	sort.SliceStable(rows, func(i, j int) bool {
		if rows[i].status != rows[j].status {
			return rows[i].status < rows[j].status
		}
		if bestBy == bestKeySpeed && rows[i].speed != rows[j].speed {
			return rows[i].speed > rows[j].speed
		}
		return lessLossDur(rows[i].loss, rows[i].latency, rows[j].loss, rows[j].latency)
	})

	headers := append([]string{"SUBNET", "ENDPOINT", "ENDPOINT PING"}, tunHeaders(ping)...)
	headers = append(headers, speedHeaders(speed)...)
	headers = append(headers, "SEEN AS", "NODE", "NODE LOCATION")
	accentCols := metricCols(2, 1+len(tunHeaders(ping))+len(speedHeaders(speed)))

	fmt.Fprintln(w, "\n"+st.title.Render("Best endpoint per subnet and node ("+bestNote(ping)+")"))
	t := table.New().
		Border(lipgloss.RoundedBorder()).
		BorderStyle(st.dim).
		Headers(headers...).
		StyleFunc(func(row, col int) lipgloss.Style {
			if row == table.HeaderRow {
				return st.cell.Bold(true)
			}
			switch rows[row].status {
			case statusTorn:
				return st.cell.Foreground(warnColor)
			case statusNone:
				if col == 1 {
					return st.cell.Foreground(failColor)
				}
				return st.cell.Foreground(dimColor)
			}
			if col == 1 {
				return st.cell.Bold(true)
			}
			if accentCols[col] {
				return st.cell.Foreground(accentColor)
			}
			return st.cell
		})
	for _, pr := range rows {
		t.Row(pr.cells...)
	}
	fmt.Fprintln(w, t.Render())
}

func uniqueSorted(working []endpointResult, key func(endpointResult) string, flagFor func(string) string) string {
	seen := make(map[string]struct{})
	for _, r := range working {
		if v := key(r); v != "" {
			seen[v] = struct{}{}
		}
	}
	vals := make([]string, 0, len(seen))
	for v := range seen {
		vals = append(vals, v)
	}
	sort.Strings(vals)
	for i, v := range vals {
		vals[i] = withFlag(flagFor(v), v)
	}
	return strings.Join(vals, "  ")
}

// Set from -sweep-ports (flags.go), for the reason bestBy is a global: a run that
// sweeps ports asks to compare them, so each ip:port keeps its own row instead of
// collapsing into the fastest port of its node.
var sweepingPorts bool

func pickKey(r endpointResult) string {
	if sweepingPorts {
		return r.endpoint
	}
	return exitColo(r.exit)
}

func nodePicks(working []endpointResult) []endpointResult {
	byNode := make(map[string][]endpointResult)
	for _, r := range working {
		byNode[pickKey(r)] = append(byNode[pickKey(r)], r)
	}
	picks := make([]endpointResult, 0, len(byNode))
	for _, group := range byNode {
		picks = append(picks, bestByPing(group))
	}
	sort.Slice(picks, func(i, j int) bool { return lessByLossRTT(picks[i], picks[j]) })
	return picks
}

func speedTargets(results []endpointResult) []endpointResult {
	working := workingSorted(results)
	seen := make(map[string]bool, len(pools))
	var out []endpointResult
	add := func(r endpointResult) {
		if !seen[r.endpoint] {
			seen[r.endpoint] = true
			out = append(out, r)
		}
	}
	for _, p := range pools {
		for _, r := range nodePicks(subnetEndpoints(working, p)) {
			add(r)
		}
	}
	for _, r := range nodePicks(working) {
		add(r)
	}
	sort.Slice(out, func(i, j int) bool { return lessByLossRTT(out[i], out[j]) })
	return out
}

func writeNodePicks(w io.Writer, working []endpointResult, ping, speed bool) {
	picks := nodePicks(working)
	fmt.Fprintf(w, "\n# Best endpoint per node (%s)\n", bestNote(ping))
	fmt.Fprintf(w, nodePickRowFmt, "NODE", "ENDPOINT", "ENDPOINT PING", tunFields("TUN PING", "LOSS", ping), speedField("SPEED", speed), "SEEN AS", "NODE LOCATION")
	for _, r := range picks {
		fmt.Fprintf(w, nodePickRowFmt, exitColo(r.exit), r.endpoint, r.epPingStr(), tunFieldsOf(r, ping), speedField(speedStr(r.speed), speed), exitRegion(r.exit), exitColoLocation(r.exit))
	}
}

type phaseResult struct {
	run     protoRun
	results []endpointResult
}

func writeToFile(path string, ph phaseResult, ping bool) error {
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	defer f.Close()
	writeFullReport(f, ph.results, ping)
	return nil
}
