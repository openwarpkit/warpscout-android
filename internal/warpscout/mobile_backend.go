package warpscout

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"runtime/debug"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/vernette/warpscout/core"
)

type MobileBackend struct {
	mu sync.Mutex
}

func NewMobileBackend() *MobileBackend {
	return &MobileBackend{}
}

func (b *MobileBackend) Register(ctx context.Context, input core.RegisterOptions, sink core.EventSink) (core.Account, error) {
	b.mu.Lock()
	defer b.mu.Unlock()

	resetMobileState()
	opts := options{
		tunnelParallel: defaultTunnelJobs,
		timeoutSec:     positiveOr(input.TimeoutSec, 2),
		perSubnet:      registerSample,
		proto:          protoAWG,
		proxy:          strings.TrimSpace(input.Proxy),
		relay:          strings.TrimSpace(input.Relay),
		freshAccount:   input.Fresh,
		ipv6:           input.IPv6,
		plain:          true,
	}
	if opts.relay == "" {
		opts.relay = defaultRelay
	}
	if err := applyRelay(&opts); err != nil {
		return core.Account{}, mobileFailure("invalid_relay", err, false)
	}
	if err := applyMobileTarget(&opts, input.CustomTarget); err != nil {
		return core.Account{}, mobileFailure("invalid_target", err, false)
	}
	_, ips, err := setupScan(opts)
	if err != nil {
		return core.Account{}, mobileFailure("network_unavailable", err, true)
	}

	emitProgress(sink, core.OperationRegister, "registration", 0, 1, "Registering WARP account")
	a, err := obtainAccount(ctx, opts, ips, time.Duration(opts.timeoutSec)*time.Second, account{})
	if err != nil {
		return core.Account{}, operationFailure(ctx, "registration_failed", err, true)
	}
	raw, err := json.Marshal(a)
	if err != nil {
		return core.Account{}, mobileFailure("account_encoding_failed", err, false)
	}
	emitProgress(sink, core.OperationRegister, "registration", 1, 1, "Account registered")
	return core.Account{RawJSON: string(raw)}, nil
}

func (b *MobileBackend) Scan(ctx context.Context, input core.Account, scan core.ScanOptions, sink core.EventSink) (core.ScanReport, error) {
	b.mu.Lock()
	defer b.mu.Unlock()

	started := time.Now().UTC()
	opts, err := mobileScanOptions(scan)
	if err != nil {
		return core.ScanReport{}, err
	}
	a, err := mobileAccount(input)
	if err != nil {
		return core.ScanReport{}, err
	}
	applyAccount(a)
	run, ips, err := setupScan(opts)
	if err != nil {
		return core.ScanReport{}, mobileFailure("scan_setup_failed", err, false)
	}
	if run.isMASQUE() && masqueAcct == nil {
		return core.ScanReport{}, mobileFailure("masque_account_missing", errors.New("account has no MASQUE device"), false)
	}

	timeout := time.Duration(opts.timeoutSec) * time.Second
	if opts.through != "" {
		n, dialErr := dialOuter(ctx, opts, timeout)
		if dialErr != nil {
			return core.ScanReport{}, operationFailure(ctx, "outer_tunnel_failed", dialErr, true)
		}
		outer = n
		defer func() {
			n.tunnel.Close()
			outer = nil
		}()
	}

	progress := newMobileEmitter(core.OperationScan, sink)
	ph, err := runScan(ctx, opts, run, ips, timeout, progress.Emit)
	if err != nil {
		return core.ScanReport{}, operationFailure(ctx, "scan_failed", err, true)
	}
	if filtered(opts) {
		ph = applyFilters(ph, opts)
	}
	if !anyEndpoint(ph) {
		return core.ScanReport{}, mobileFailure("no_endpoints", errors.New(noEndpointMsg(opts)), true)
	}
	if opts.speed || opts.bestBy == bestKeySpeed {
		measureSpeed(ctx, ph, timeout, progress.Emit)
	}

	results := mobileResults(ph.results)
	return core.ScanReport{
		Protocol:   core.Protocol(run.name),
		StartedAt:  started,
		FinishedAt: time.Now().UTC(),
		Results:    results,
	}, nil
}

func (b *MobileBackend) FindJunk(ctx context.Context, input core.Account, scan core.ScanOptions, sink core.EventSink) (core.JunkProfile, error) {
	b.mu.Lock()
	defer b.mu.Unlock()

	scan.Protocol = core.ProtocolAWG
	scan.InnerProtocol = ""
	scan.ThroughEndpoint = ""
	scan.SamplePerSubnet = positiveOr(scan.SamplePerSubnet, findJunkSample)
	scan.TunnelPingCount = max(scan.TunnelPingCount, durabilityPings)
	opts, err := mobileScanOptions(scan)
	if err != nil {
		return core.JunkProfile{}, err
	}
	opts.threshold = defaultJunkThreshold
	a, err := mobileAccount(input)
	if err != nil {
		return core.JunkProfile{}, err
	}
	applyAccount(a)
	run, _, err := setupScan(opts)
	if err != nil {
		return core.JunkProfile{}, mobileFailure("scan_setup_failed", err, false)
	}

	progress := newMobileEmitter(core.OperationFindJunk, sink)
	timeout := time.Duration(opts.timeoutSec) * time.Second
	var best junkCandidate
	var tested []core.JunkAttempt
	for attempt := 1; ; attempt++ {
		if ctx.Err() != nil {
			if best.total > 0 {
				return junkProfile(best, tested), nil
			}
			failure := operationFailure(ctx, "canceled", ctx.Err(), false)
			if typed, ok := failure.(*core.CoreError); ok {
				typed.Payload = core.JunkProfile{Tested: tested}
			}
			return core.JunkProfile{}, failure
		}
		genJunkParams()
		if scan.AWGI1 != "" {
			awgI1 = scan.AWGI1
		}
		current := core.JunkAttempt{
			JunkCount: awgJc,
			JunkMin:   awgJmin,
			JunkMax:   awgJmax,
			I1:        awgI1,
		}
		emitProgress(sink, core.OperationFindJunk, "junk", attempt-1, 0, fmt.Sprintf("Attempt %d", attempt))
		ph, scanErr := runScan(ctx, opts, run, expandPools(opts.perSubnet), timeout, progress.Emit)
		if ctx.Err() != nil {
			tested = append(tested, current)
			continue
		}
		if scanErr != nil {
			tested = append(tested, current)
			continue
		}
		candidate := scoreJunk(ph)
		current.Working = candidate.working
		current.Total = candidate.total
		current.Completed = true
		tested = append(tested, current)
		if candidate.working > best.working {
			best = candidate
		}
		if candidate.meets(opts.threshold) {
			return junkProfile(candidate, tested), nil
		}
	}
}

func (b *MobileBackend) FindSNI(ctx context.Context, input core.Account, scan core.ScanOptions, sink core.EventSink) (core.SNIProfile, error) {
	b.mu.Lock()
	defer b.mu.Unlock()

	if scan.Protocol == "" {
		scan.Protocol = core.ProtocolMASQUEH3
	}
	if scan.Protocol != core.ProtocolMASQUEH3 && scan.Protocol != core.ProtocolMASQUEH2 {
		return core.SNIProfile{}, mobileFailure("invalid_protocol", errors.New("find-sni requires masque or masque-h2"), false)
	}
	scan.InnerProtocol = ""
	scan.ThroughEndpoint = ""
	scan.SamplePerSubnet = findSNISample
	scan.TunnelPingCount = max(scan.TunnelPingCount, durabilityPings)
	opts, err := mobileScanOptions(scan)
	if err != nil {
		return core.SNIProfile{}, err
	}
	opts.threshold = defaultSNIThreshold
	a, err := mobileAccount(input)
	if err != nil {
		return core.SNIProfile{}, err
	}
	applyAccount(a)
	run, ips, err := setupScan(opts)
	if err != nil {
		return core.SNIProfile{}, mobileFailure("scan_setup_failed", err, false)
	}
	if masqueAcct == nil {
		return core.SNIProfile{}, mobileFailure("masque_account_missing", errors.New("account has no MASQUE device"), false)
	}

	progress := newMobileEmitter(core.OperationFindSNI, sink)
	timeout := time.Duration(opts.timeoutSec) * time.Second
	var best sniCandidate
	attempts := 0
	var tested []core.SNIAttempt
	for i, sni := range sniCandidates {
		if ctx.Err() != nil {
			break
		}
		attempts++
		masqueSNI = sni
		emitProgress(sink, core.OperationFindSNI, "sni", i, len(sniCandidates), sni)
		ph, scanErr := runScan(ctx, opts, run, ips, timeout, progress.Emit)
		if ctx.Err() != nil {
			tested = append(tested, core.SNIAttempt{SNI: sni})
			break
		}
		if scanErr != nil {
			tested = append(tested, core.SNIAttempt{SNI: sni})
			continue
		}
		candidate := scoreSNI(sni, ph)
		tested = append(tested, core.SNIAttempt{
			SNI:       sni,
			Working:   candidate.working,
			Total:     candidate.total,
			Completed: true,
		})
		if candidate.working > best.working {
			best = candidate
		}
		if candidate.meets(opts.threshold) {
			return sniProfile(candidate, scan.Protocol, attempts, tested), nil
		}
	}
	if best.total > 0 {
		return sniProfile(best, scan.Protocol, attempts, tested), nil
	}
	failure := operationFailure(ctx, "sni_not_found", errors.New("no SNI completed a scan"), true)
	if typed, ok := failure.(*core.CoreError); ok {
		typed.Payload = core.SNIProfile{Protocol: scan.Protocol, Attempts: attempts, Tested: tested}
	}
	return core.SNIProfile{}, failure
}

func (b *MobileBackend) StartSocks(ctx context.Context, input core.Account, socks core.SocksOptions, sink core.EventSink) error {
	b.mu.Lock()
	defer b.mu.Unlock()

	scan := socks.Scan
	if socks.Protocol != "" {
		scan.Protocol = socks.Protocol
	}
	opts, err := mobileScanOptions(scan)
	if err != nil {
		return err
	}
	opts.endpoint = strings.TrimSpace(socks.Endpoint)
	opts.listen = defaultSocksListen
	opts.port = socks.Port
	if opts.endpoint == "" {
		return mobileFailure("endpoint_required", errors.New("SOCKS endpoint is required"), false)
	}
	endpoint, err := parseEndpointSpec("endpoint", opts.endpoint)
	if err != nil {
		return mobileFailure("invalid_endpoint", err, false)
	}
	ap, err := netip.ParseAddrPort(endpoint)
	if err != nil {
		return mobileFailure("invalid_endpoint", err, false)
	}
	opts.ipv6 = !ap.Addr().Is4()

	a, err := mobileAccount(input)
	if err != nil {
		return err
	}
	applyAccount(a)
	run, _, err := setupScan(opts)
	if err != nil {
		return mobileFailure("socks_setup_failed", err, false)
	}
	if run.isMASQUE() && masqueAcct == nil {
		return mobileFailure("masque_account_missing", errors.New("account has no MASQUE device"), false)
	}

	timeout := time.Duration(opts.timeoutSec) * time.Second
	if opts.through != "" {
		n, dialErr := dialOuter(ctx, opts, timeout)
		if dialErr != nil {
			return operationFailure(ctx, "outer_tunnel_failed", dialErr, true)
		}
		outer = n
		defer func() {
			n.tunnel.Close()
			outer = nil
		}()
	}

	emitProgress(sink, core.OperationSocks, "handshake", 0, 1, endpoint)
	tn, err := newTunnel(run)
	if err != nil {
		return mobileFailure("tunnel_setup_failed", err, false)
	}
	defer tn.Close()
	if !tn.handshake(ctx, endpoint, timeout) {
		return operationFailure(ctx, "endpoint_unreachable", fmt.Errorf("%s did not answer over %s", endpoint, run.name), true)
	}
	defer debug.SetGCPercent(debug.SetGCPercent(speedGCPercent))

	ln, err := net.Listen("tcp", net.JoinHostPort(defaultSocksListen, strconv.Itoa(socks.Port)))
	if err != nil {
		return mobileFailure("listen_failed", err, true)
	}
	defer ln.Close()
	emitProgress(sink, core.OperationSocks, "listening", 1, 1, "socks5h://"+ln.Addr().String())

	go func() {
		<-ctx.Done()
		ln.Close()
	}()
	for {
		connection, acceptErr := ln.Accept()
		if acceptErr != nil {
			if ctx.Err() != nil {
				return nil
			}
			return mobileFailure("accept_failed", acceptErr, true)
		}
		go serveSOCKS(ctx, connection, tn.stack().tnet)
	}
}

func (b *MobileBackend) RenderReport(report core.ScanReport) (string, error) {
	b.mu.Lock()
	defer b.mu.Unlock()

	var out strings.Builder
	fmt.Fprintf(&out, "WARPSCOUT scan report\nProtocol: %s\nStarted: %s\nFinished: %s\n\n", report.Protocol, report.StartedAt.UTC().Format(time.RFC3339), report.FinishedAt.UTC().Format(time.RFC3339))
	fmt.Fprintln(&out, "ENDPOINT\tSTATUS\tREGION\tNODE\tCOUNTRY\tNODE LOCATION\tENDPOINT PING\tTUNNEL PING\tLOSS\tSPEED")
	for _, result := range report.Results {
		status := "working"
		if result.Working && !result.Durable {
			status = "torn-down"
		} else if !result.Working {
			status = "failed"
		}
		fmt.Fprintf(&out, "%s\t%s\t%s\t%s\t%s\t%s\t%.1f ms\t%.1f ms\t%.0f%%\t%.1f Mbps\n", result.Endpoint, status, result.Region, result.Node, result.Country, result.NodeLocation, result.EndpointPingMS, result.TunnelPingMS, result.LossPercent, result.SpeedMbps)
	}
	return out.String(), nil
}

func (b *MobileBackend) RenderConfig(input core.Account, endpoint core.EndpointResult, scan core.ScanOptions, format core.ConfigFormat) (string, error) {
	b.mu.Lock()
	defer b.mu.Unlock()

	if strings.TrimSpace(endpoint.Endpoint) == "" {
		return "", mobileFailure("endpoint_required", errors.New("endpoint is required"), false)
	}
	switch format {
	case core.ConfigWireGuard:
		scan.Protocol = core.ProtocolWG
	case core.ConfigAmneziaWG:
		scan.Protocol = core.ProtocolAWG
	case core.ConfigUSQUE:
		if scan.Protocol != core.ProtocolMASQUEH2 {
			scan.Protocol = core.ProtocolMASQUEH3
		}
	case core.ConfigMihomo:
	default:
		return "", mobileFailure("invalid_config_format", fmt.Errorf("unsupported format %q", format), false)
	}
	opts, err := mobileScanOptions(scan)
	if err != nil {
		return "", err
	}
	if format == core.ConfigMihomo {
		opts.confType = confTypeMihomo
	}
	a, err := mobileAccount(input)
	if err != nil {
		return "", err
	}
	applyAccount(a)
	run, err := parseProto(opts.proto)
	if err != nil {
		return "", mobileFailure("invalid_protocol", err, false)
	}
	if run.isMASQUE() && masqueAcct == nil {
		return "", mobileFailure("masque_account_missing", errors.New("account has no MASQUE device"), false)
	}
	data, err := renderConfFor(opts, endpoint.Endpoint, run)
	if err != nil {
		return "", mobileFailure("config_render_failed", err, false)
	}
	return string(data), nil
}

func mobileScanOptions(scan core.ScanOptions) (options, error) {
	resetMobileState()
	protocol := scan.Protocol
	if protocol == "" {
		protocol = core.ProtocolWG
	}
	inner := scan.InnerProtocol
	if inner == "" {
		inner = core.ProtocolWG
	}
	opts := options{
		tunnelParallel: positiveOr(scan.Jobs, defaultTunnelJobs),
		timeoutSec:     positiveOr(scan.TimeoutSec, 2),
		perSubnet:      scan.SamplePerSubnet,
		tunPingCount:   scan.TunnelPingCount,
		mtu:            scan.MTU,
		port:           scan.Port,
		proto:          string(protocol),
		confType:       confTypeNative,
		dns:            strings.Join(scan.DNS, ", "),
		through:        strings.TrimSpace(scan.ThroughEndpoint),
		innerProto:     string(inner),
		wantMeta:       true,
		ipv6:           scan.IPv6,
		full:           scan.Full,
		speed:          scan.SpeedTest,
		bestBy:         strings.TrimSpace(scan.BestBy),
		sweepPorts:     strings.TrimSpace(scan.SweepPorts),
		pingTarget:     strings.TrimSpace(scan.PingTarget),
		plain:          true,
	}
	if !opts.full && opts.perSubnet <= 0 {
		opts.perSubnet = 5
	}
	if opts.tunPingCount > 0 && opts.tunPingCount < minDurabilityPings {
		return options{}, mobileFailure("invalid_tunnel_ping_count", fmt.Errorf("tunnel ping count must be at least %d", minDurabilityPings), false)
	}
	if opts.bestBy == "" {
		opts.bestBy = bestKeyPing
	}
	if !slices.Contains(bestKeys, opts.bestBy) {
		return options{}, mobileFailure("invalid_best_by", fmt.Errorf("bestBy must be one of %s", strings.Join(bestKeys, ", ")), false)
	}
	bestBy = opts.bestBy
	if opts.port < 0 || opts.port > 65535 {
		return options{}, mobileFailure("invalid_port", errors.New("port must be between 1 and 65535"), false)
	}
	if opts.mtu != 0 && (opts.mtu < mtuMin || opts.mtu > mtuMax) {
		return options{}, mobileFailure("invalid_mtu", fmt.Errorf("MTU must be between %d and %d", mtuMin, mtuMax), false)
	}
	if _, err := parseProto(opts.proto); err != nil {
		return options{}, mobileFailure("invalid_protocol", err, false)
	}
	if opts.sweepPorts != "" {
		if !slices.Contains(sweepModes, opts.sweepPorts) {
			return options{}, mobileFailure("invalid_sweep_ports", fmt.Errorf("sweepPorts must be one of %s", strings.Join(sweepModes, ", ")), false)
		}
		if opts.port != 0 {
			return options{}, mobileFailure("conflicting_port_options", errors.New("sweepPorts and port cannot be used together"), false)
		}
		if opts.proto == protoMASQUE || opts.proto == protoMASQUEH2 {
			return options{}, mobileFailure("unsupported_sweep_ports", errors.New("port sweeping does not apply to MASQUE"), false)
		}
		sweepingPorts = true
	}
	if opts.pingTarget != "" {
		if opts.tunPingCount <= 0 {
			return options{}, mobileFailure("ping_target_requires_tunnel_ping", errors.New("pingTarget requires tunnel pings"), false)
		}
		if addr, err := netip.ParseAddr(opts.pingTarget); err == nil {
			pingTarget = addr.String()
		} else {
			if strings.ContainsAny(opts.pingTarget, ":/ ") {
				return options{}, mobileFailure("invalid_ping_target", errors.New("pingTarget must be an IP address or hostname"), false)
			}
			pingTarget = opts.pingTarget
		}
	}
	if opts.through != "" {
		if opts.proto != protoWG && opts.proto != protoAWG {
			return options{}, mobileFailure("invalid_outer_protocol", errors.New("outer protocol must be wg or awg"), false)
		}
		if opts.innerProto != protoWG && opts.innerProto != protoAWG {
			return options{}, mobileFailure("invalid_inner_protocol", errors.New("inner protocol must be wg or awg"), false)
		}
		if _, err := parseEndpointSpec("throughEndpoint", opts.through); err != nil {
			return options{}, mobileFailure("invalid_outer_endpoint", err, false)
		}
		opts.tunnelParallel = nestedTunnelJobs
	}
	if err := applyMobileTarget(&opts, scan.CustomTarget); err != nil {
		return options{}, mobileFailure("invalid_target", err, false)
	}
	opts.colos = normalizedCodes(scan.IncludeNodes)
	opts.countries = normalizedCodes(scan.IncludeCountries)
	opts.dropColos = normalizedCodes(scan.ExcludeNodes)
	opts.dropCountries = normalizedCodes(scan.ExcludeCountries)
	if (opts.proto == protoMASQUE || opts.proto == protoMASQUEH2) && filtered(opts) {
		return options{}, mobileFailure("unsupported_filter", errors.New("node and country filters do not apply to MASQUE"), false)
	}
	if scan.AWGJunkCount != 0 {
		awgJc = scan.AWGJunkCount
	}
	if scan.AWGJunkMin != 0 {
		awgJmin = scan.AWGJunkMin
	}
	if scan.AWGJunkMax != 0 {
		awgJmax = scan.AWGJunkMax
	}
	if awgJc < junkCountLimitMin || awgJc > junkCountLimitMax || awgJmin > awgJmax || awgJmax > tunnelMTU {
		return options{}, mobileFailure("invalid_junk_parameters", errors.New("invalid AmneziaWG junk parameters"), false)
	}
	if scan.AWGI1 != "" {
		awgI1 = scan.AWGI1
	}
	if scan.MASQUESNI != "" {
		masqueSNI = scan.MASQUESNI
	}
	masqueAttempts = positiveOr(scan.MASQUEAttempts, masqueDefaultAttempts)
	return opts, nil
}

func mobileAccount(input core.Account) (account, error) {
	if strings.TrimSpace(input.RawJSON) == "" {
		return account{}, mobileFailure("account_required", errors.New("WARP account is required"), false)
	}
	var a account
	if err := json.Unmarshal([]byte(input.RawJSON), &a); err != nil {
		return account{}, mobileFailure("invalid_account", errors.New("account JSON is invalid"), false)
	}
	if a.PrivateKey == "" || a.PeerPublicKey == "" {
		return account{}, mobileFailure("invalid_account", errors.New("account is incomplete"), false)
	}
	return a, nil
}

func applyMobileTarget(opts *options, target string) error {
	if strings.TrimSpace(target) == "" {
		return nil
	}
	targets, err := parseTargets(target)
	if err != nil {
		return err
	}
	opts.targets = targets
	opts.target = target
	opts.ipv6 = !targets[0].Addr().Is4()
	return nil
}

func resetMobileState() {
	scanInterface = ""
	scanSourceIP = netip.Addr{}
	outer = nil
	outerAcct = nil
	masqueAcct = nil
	pools = poolsV4
	warpPorts = append([]int(nil), primaryWarpPorts...)
	bestBy = bestKeyPing
	sweepingPorts = false
	pingTarget = tunnelDNS
	pingAddr.Store(nil)
	awgJc = 6
	awgJmin = 10
	awgJmax = 50
	awgI1 = i1Default
	genI1Label = ""
	masqueAttempts = masqueDefaultAttempts
	masqueSNI = masqueDefaultSNI
}

func mobileResults(results []endpointResult) []core.EndpointResult {
	ordered := append(workingSorted(results), tornSorted(results)...)
	out := make([]core.EndpointResult, 0, len(ordered))
	for _, result := range ordered {
		out = append(out, mobileEndpoint(result))
	}
	return out
}

func mobileEndpoint(result endpointResult) core.EndpointResult {
	region := result.exit.loc
	node := result.exit.colo
	country := result.exit.coloISO
	location := result.exit.coloCity
	if location != "" && country != "" {
		location += ", " + country
	} else if location == "" {
		location = country
	}
	return core.EndpointResult{
		Endpoint:       result.endpoint,
		Region:         region,
		Node:           node,
		Country:        country,
		NodeLocation:   location,
		EndpointPingMS: durationMilliseconds(result.epPing),
		TunnelPingMS:   durationMilliseconds(result.tunPing),
		LossPercent:    float64(result.loss) * 100,
		SpeedMbps:      result.speed,
		Working:        result.ok,
		Durable:        result.durable,
	}
}

func durationMilliseconds(value time.Duration) float64 {
	return float64(value) / float64(time.Millisecond)
}

func junkProfile(candidate junkCandidate, tested []core.JunkAttempt) core.JunkProfile {
	for index := range tested {
		attempt := &tested[index]
		attempt.Selected = attempt.Completed &&
			attempt.JunkCount == candidate.jc &&
			attempt.JunkMin == candidate.jmin &&
			attempt.JunkMax == candidate.jmax &&
			attempt.I1 == candidate.i1 &&
			attempt.Working == candidate.working &&
			attempt.Total == candidate.total
	}
	return core.JunkProfile{
		JunkCount: candidate.jc,
		JunkMin:   candidate.jmin,
		JunkMax:   candidate.jmax,
		I1:        candidate.i1,
		Tested:    tested,
	}
}

func sniProfile(candidate sniCandidate, protocol core.Protocol, attempts int, tested []core.SNIAttempt) core.SNIProfile {
	for index := range tested {
		tested[index].Selected = tested[index].Completed && tested[index].SNI == candidate.sni
	}
	return core.SNIProfile{
		SNI:      candidate.sni,
		Protocol: protocol,
		Attempts: attempts,
		Tested:   tested,
	}
}

func normalizedCodes(values []string) []string {
	out := make([]string, 0, len(values))
	for _, value := range values {
		if code := strings.ToUpper(strings.TrimSpace(value)); code != "" {
			out = append(out, code)
		}
	}
	return out
}

func positiveOr(value, fallback int) int {
	if value > 0 {
		return value
	}
	return fallback
}

func mobileFailure(code string, err error, retryable bool) error {
	return &core.CoreError{Code: code, Message: err.Error(), Retryable: retryable}
}

func operationFailure(ctx context.Context, code string, err error, retryable bool) error {
	if ctx.Err() != nil {
		return mobileFailure("canceled", ctx.Err(), false)
	}
	return mobileFailure(code, err, retryable)
}

func emitProgress(sink core.EventSink, operation core.Operation, phase string, completed, total int, message string) {
	if sink == nil {
		return
	}
	sink(core.ProgressEvent{Operation: operation, Type: "progress", Phase: phase, Completed: completed, Total: total, Message: message})
}

type mobileEmitter struct {
	operation core.Operation
	sink      core.EventSink
	mu        sync.Mutex
	phase     string
	completed int
	total     int
}

func newMobileEmitter(operation core.Operation, sink core.EventSink) *mobileEmitter {
	return &mobileEmitter{operation: operation, sink: sink}
}

func (e *mobileEmitter) Emit(message tea.Msg) {
	if e.sink == nil {
		return
	}
	e.mu.Lock()
	defer e.mu.Unlock()

	event := core.ProgressEvent{Operation: e.operation, Type: "progress", Phase: e.phase, Completed: e.completed, Total: e.total}
	switch value := message.(type) {
	case stepMsg:
		e.phase = value.label
		event.Phase = value.label
		event.Message = value.summary
	case barBeginMsg:
		e.phase = value.label
		e.completed = 0
		e.total = value.total
		event.Phase = value.label
		event.Total = value.total
	case probedMsg:
		e.completed++
		event.Completed = e.completed
	case foundMsg:
		endpoint := core.EndpointResult{
			Endpoint:       value.endpoint,
			Region:         value.exit,
			Node:           value.colo,
			EndpointPingMS: durationMilliseconds(value.epPing),
			TunnelPingMS:   durationMilliseconds(value.tunPing),
			LossPercent:    float64(value.loss) * 100,
			Working:        true,
			Durable:        !value.torn,
		}
		event.Type = "endpoint"
		event.Endpoint = &endpoint
	case speedMsg:
		event.Type = "speed"
		event.Message = value.endpoint
		endpoint := core.EndpointResult{Endpoint: value.endpoint, SpeedMbps: value.mbps}
		event.Endpoint = &endpoint
	case barEndMsg:
		e.phase = value.label
		event.Phase = value.label
		event.Message = value.summary
	case doneMsg:
		event.Type = "phase-completed"
	default:
		return
	}
	e.sink(event)
}
