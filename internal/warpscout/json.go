package warpscout

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/netip"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/vernette/warpscout/core"
)

const (
	jsonSchemaURL = "https://raw.githubusercontent.com/vernette/warpscout/master/schema/warpscout-v1.schema.json"
	jsonVersion   = 1
)

const (
	phasePorts   = "ports"
	phaseTunnels = "tunnels"
	phaseSpeed   = "speed"
)

type jsonEndpoint struct {
	Endpoint    string   `json:"endpoint"`
	IP          string   `json:"ip"`
	Port        int      `json:"port"`
	SeenAs      *string  `json:"seen_as"`
	Node        *string  `json:"node"`
	NodeCity    *string  `json:"node_city"`
	NodeCountry *string  `json:"node_country"`
	PingMS      *float64 `json:"ping_ms"`
	TunPingMS   *float64 `json:"tun_ping_ms"`
	LossPct     *float64 `json:"loss_pct"`
	SpeedMbps   *float64 `json:"speed_mbps"`
	Measured    bool     `json:"measured"`
	Torn        bool     `json:"torn"`
}

type jsonResult struct {
	Schema       string         `json:"$schema"`
	Version      int            `json:"version"`
	Proto        string         `json:"proto"`
	Total        int            `json:"total"`
	WorkingCount int            `json:"working_count"`
	Best         *string        `json:"best"`
	Picks        []jsonEndpoint `json:"picks"`
	Working      []jsonEndpoint `json:"working"`
	Torn         []jsonEndpoint `json:"torn"`
}

type jsonProgressEvent struct {
	Event string `json:"event"`
	Phase string `json:"phase"`
	Done  int    `json:"done"`
	Total int    `json:"total"`
}

type jsonEndpointEvent struct {
	Event    string       `json:"event"`
	Endpoint jsonEndpoint `json:"endpoint"`
}

type jsonDoneEvent struct {
	Event  string     `json:"event"`
	Result jsonResult `json:"result"`
}

type jsonErrorEvent struct {
	Event string `json:"event"`
	Error string `json:"error"`
}

func optString(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}

func optMS(d time.Duration) *float64 {
	if d <= 0 {
		return nil
	}
	ms := float64(d.Microseconds()) / 1000
	return &ms
}

func toJSONEndpoint(r endpointResult) jsonEndpoint {
	ep := jsonEndpoint{
		Endpoint:    r.endpoint,
		IP:          r.ip.String(),
		SeenAs:      optString(r.exit.loc),
		Node:        optString(r.exit.colo),
		NodeCity:    optString(r.exit.coloCity),
		NodeCountry: optString(r.exit.coloISO),
		PingMS:      optMS(r.epPing),
		Measured:    r.measured,
		Torn:        r.ok && !r.durable,
	}
	if ap, err := netip.ParseAddrPort(r.endpoint); err == nil {
		ep.IP, ep.Port = ap.Addr().String(), int(ap.Port())
	}
	if r.measured {
		ep.TunPingMS = optMS(r.tunPing)
		loss := math.Round(float64(r.loss)*1000) / 10
		ep.LossPct = &loss
	}
	if r.speed > 0 {
		speed := math.Round(r.speed*10) / 10
		ep.SpeedMbps = &speed
	}
	return ep
}

func toJSONEndpoints(rs []endpointResult) []jsonEndpoint {
	out := make([]jsonEndpoint, len(rs))
	for i, r := range rs {
		out[i] = toJSONEndpoint(r)
	}
	return out
}

func makeJSONResult(ph phaseResult) jsonResult {
	working := workingSorted(ph.results)
	res := jsonResult{
		Schema:       jsonSchemaURL,
		Version:      jsonVersion,
		Proto:        ph.run.name,
		Total:        len(ph.results),
		WorkingCount: len(working),
		Picks:        toJSONEndpoints(nodePicks(working)),
		Working:      toJSONEndpoints(working),
		Torn:         toJSONEndpoints(tornSorted(ph.results)),
	}
	if best, ok := bestOverall(ph); ok {
		res.Best = &best.endpoint
	}
	return res
}

func writeJSONResult(w io.Writer, ph phaseResult, stream bool) error {
	result := makeJSONResult(ph)
	if stream {
		return json.NewEncoder(w).Encode(jsonDoneEvent{Event: "done", Result: result})
	}
	return writePrettyJSONResult(w, result)
}

func writePrettyJSONResult(w io.Writer, result jsonResult) error {
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	return enc.Encode(result)
}

func RenderJSONReport(report core.ScanReport, scan core.ScanOptions) (string, error) {
	if _, err := parseProto(string(report.Protocol)); err != nil {
		return "", err
	}
	result := jsonResult{
		Schema:  jsonSchemaURL,
		Version: jsonVersion,
		Proto:   string(report.Protocol),
		Total:   max(report.TestedCount, len(report.Results)),
		Picks:   []jsonEndpoint{},
		Working: []jsonEndpoint{},
		Torn:    []jsonEndpoint{},
	}
	picked := make(map[string]bool)
	for _, saved := range report.Results {
		if !saved.Working {
			continue
		}
		node := jsonReportCode(saved.Node, 3)
		city := strings.TrimSuffix(saved.NodeLocation, ", "+saved.Country)
		if city == saved.Country {
			city = ""
		}
		ep := toJSONEndpoint(endpointResult{
			endpoint: saved.Endpoint,
			exit: metaResult{
				loc:      jsonReportCode(saved.Region, 2),
				colo:     node,
				coloCity: city,
				coloISO:  jsonReportCode(saved.Country, 2),
			},
			epPing:   time.Duration(math.Round(saved.EndpointPingMS * float64(time.Millisecond))),
			tunPing:  time.Duration(math.Round(saved.TunnelPingMS * float64(time.Millisecond))),
			loss:     float32(saved.LossPercent / 100),
			speed:    saved.SpeedMbps,
			measured: scan.TunnelPingCount > 0 || saved.TunnelPingMS > 0 || saved.LossPercent > 0 || !saved.Durable,
			ok:       true,
			durable:  saved.Durable,
		})
		if ep.Port == 0 {
			return "", fmt.Errorf("invalid report endpoint %q", saved.Endpoint)
		}
		if ep.Torn {
			result.Torn = append(result.Torn, ep)
			continue
		}
		result.Working = append(result.Working, ep)
		key := node
		if scan.SweepPorts != "" {
			key = ep.Endpoint
		}
		if !picked[key] {
			result.Picks = append(result.Picks, ep)
			picked[key] = true
		}
	}
	result.WorkingCount = len(result.Working)
	if result.WorkingCount > 0 {
		result.Best = &result.Working[0].Endpoint
	}
	var out bytes.Buffer
	if err := writePrettyJSONResult(&out, result); err != nil {
		return "", err
	}
	return out.String(), nil
}

func jsonReportCode(value string, length int) string {
	value = strings.ToUpper(strings.TrimSpace(value))
	if len(value) != length {
		return ""
	}
	for _, char := range value {
		if char < 'A' || char > 'Z' {
			return ""
		}
	}
	return value
}

type jsonStreamEmitter struct {
	mu    sync.Mutex
	enc   *json.Encoder
	total int
	phase string
	done  atomic.Int64
}

func newJSONStreamEmitter(out io.Writer) *jsonStreamEmitter {
	return &jsonStreamEmitter{enc: json.NewEncoder(out)}
}

func (e *jsonStreamEmitter) send(v any) {
	e.mu.Lock()
	defer e.mu.Unlock()
	_ = e.enc.Encode(v)
}

func phaseOf(label string) string {
	switch {
	case strings.HasPrefix(label, "Phase 1"):
		return phasePorts
	case strings.HasPrefix(label, "Speedtest"):
		return phaseSpeed
	}
	return phaseTunnels
}

func (e *jsonStreamEmitter) emit(msg tea.Msg) {
	switch m := msg.(type) {
	case stepMsg:
		if m.fail {
			e.send(jsonErrorEvent{Event: "error", Error: m.summary})
		}
	case barBeginMsg:
		e.total, e.phase = m.total, phaseOf(m.label)
		e.done.Store(0)
		e.send(jsonProgressEvent{Event: "progress", Phase: e.phase, Done: 0, Total: m.total})
	case probedMsg:
		e.send(jsonProgressEvent{Event: "progress", Phase: e.phase, Done: int(e.done.Add(1)), Total: e.total})
	case foundMsg:
		e.send(jsonEndpointEvent{Event: "endpoint", Endpoint: toJSONEndpoint(m.result)})
	}
}
