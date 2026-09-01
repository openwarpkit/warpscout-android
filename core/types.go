package core

import (
	"encoding/json"
	"fmt"
	"time"
)

type Operation string

const (
	OperationRegister Operation = "register"
	OperationScan     Operation = "scan"
	OperationFindJunk Operation = "find-junk"
	OperationFindSNI  Operation = "find-sni"
	OperationSocks    Operation = "socks"
	OperationReport   Operation = "render-report"
	OperationConfig   Operation = "render-config"
)

type Protocol string

const (
	ProtocolWG       Protocol = "wg"
	ProtocolAWG      Protocol = "awg"
	ProtocolMASQUEH3 Protocol = "masque"
	ProtocolMASQUEH2 Protocol = "masque-h2"
)

type Account struct {
	RawJSON string `json:"rawJson"`
}

type RegisterOptions struct {
	Proxy        string `json:"proxy,omitempty"`
	Relay        string `json:"relay,omitempty"`
	Fresh        bool   `json:"fresh"`
	TimeoutSec   int    `json:"timeoutSec"`
	IPv6         bool   `json:"ipv6"`
	CustomTarget string `json:"customTarget,omitempty"`
}

type ScanOptions struct {
	Protocol            Protocol `json:"protocol"`
	InnerProtocol       Protocol `json:"innerProtocol,omitempty"`
	IPv6                bool     `json:"ipv6"`
	Port                int      `json:"port"`
	TimeoutSec          int      `json:"timeoutSec"`
	Jobs                int      `json:"jobs"`
	SamplePerSubnet     int      `json:"samplePerSubnet"`
	Full                bool     `json:"full"`
	TunnelPingCount     int      `json:"tunnelPingCount"`
	CustomTarget        string   `json:"customTarget,omitempty"`
	AWGJunkCount        int      `json:"awgJunkCount"`
	AWGJunkMin          int      `json:"awgJunkMin"`
	AWGJunkMax          int      `json:"awgJunkMax"`
	AWGI1               string   `json:"awgI1,omitempty"`
	MASQUESNI           string   `json:"masqueSni,omitempty"`
	MASQUEAttempts      int      `json:"masqueAttempts"`
	IncludeNodes        []string `json:"includeNodes,omitempty"`
	IncludeCountries    []string `json:"includeCountries,omitempty"`
	ExcludeNodes        []string `json:"excludeNodes,omitempty"`
	ExcludeCountries    []string `json:"excludeCountries,omitempty"`
	MTU                 int      `json:"mtu"`
	DNS                 []string `json:"dns,omitempty"`
	SpeedTest           bool     `json:"speedTest"`
	BestBy              string   `json:"bestBy,omitempty"`
	SweepPorts          string   `json:"sweepPorts,omitempty"`
	PingTarget          string   `json:"pingTarget,omitempty"`
	ThroughEndpoint     string   `json:"throughEndpoint,omitempty"`
	ConfigurationFormat string   `json:"configurationFormat,omitempty"`
}

type EndpointResult struct {
	Endpoint       string  `json:"endpoint"`
	ExitIP         string  `json:"exitIp,omitempty"`
	Region         string  `json:"region,omitempty"`
	Node           string  `json:"node,omitempty"`
	Country        string  `json:"country,omitempty"`
	NodeLocation   string  `json:"nodeLocation,omitempty"`
	EndpointPingMS float64 `json:"endpointPingMs,omitempty"`
	TunnelPingMS   float64 `json:"tunnelPingMs,omitempty"`
	LossPercent    float64 `json:"lossPercent,omitempty"`
	SpeedMbps      float64 `json:"speedMbps,omitempty"`
	Working        bool    `json:"working"`
	Durable        bool    `json:"durable"`
}

type ScanReport struct {
	Protocol   Protocol         `json:"protocol"`
	StartedAt  time.Time        `json:"startedAt"`
	FinishedAt time.Time        `json:"finishedAt"`
	Results    []EndpointResult `json:"results"`
}

type ProgressEvent struct {
	SchemaVersion int             `json:"schemaVersion"`
	Operation     Operation       `json:"operation"`
	Type          string          `json:"type"`
	Phase         string          `json:"phase,omitempty"`
	Completed     int             `json:"completed,omitempty"`
	Total         int             `json:"total,omitempty"`
	Message       string          `json:"message,omitempty"`
	Endpoint      *EndpointResult `json:"endpoint,omitempty"`
	Payload       json.RawMessage `json:"payload,omitempty"`
}

type CoreError struct {
	Code      string `json:"code"`
	Message   string `json:"message"`
	Retryable bool   `json:"retryable"`
	Payload   any    `json:"payload,omitempty"`
}

func (e *CoreError) Error() string {
	return fmt.Sprintf("%s: %s", e.Code, e.Message)
}

type JunkProfile struct {
	JunkCount int           `json:"junkCount"`
	JunkMin   int           `json:"junkMin"`
	JunkMax   int           `json:"junkMax"`
	I1        string        `json:"i1,omitempty"`
	Tested    []JunkAttempt `json:"tested,omitempty"`
}

type JunkAttempt struct {
	JunkCount int    `json:"junkCount"`
	JunkMin   int    `json:"junkMin"`
	JunkMax   int    `json:"junkMax"`
	I1        string `json:"i1,omitempty"`
	Working   int    `json:"working"`
	Total     int    `json:"total"`
	Completed bool   `json:"completed"`
	Selected  bool   `json:"selected"`
}

type SNIProfile struct {
	SNI      string       `json:"sni"`
	Protocol Protocol     `json:"protocol"`
	Attempts int          `json:"attempts"`
	Tested   []SNIAttempt `json:"tested,omitempty"`
}

type SNIAttempt struct {
	SNI       string `json:"sni"`
	Working   int    `json:"working"`
	Total     int    `json:"total"`
	Completed bool   `json:"completed"`
	Selected  bool   `json:"selected"`
}

type SocksOptions struct {
	Endpoint string      `json:"endpoint"`
	Port     int         `json:"port"`
	Protocol Protocol    `json:"protocol"`
	Scan     ScanOptions `json:"scan"`
}

type ConfigFormat string

const (
	ConfigWireGuard ConfigFormat = "wireguard"
	ConfigAmneziaWG ConfigFormat = "amneziawg"
	ConfigUSQUE     ConfigFormat = "usque"
	ConfigMihomo    ConfigFormat = "mihomo"
)
