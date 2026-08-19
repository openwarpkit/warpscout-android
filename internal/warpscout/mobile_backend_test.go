package warpscout

import (
	"strings"
	"testing"
	"time"

	"github.com/vernette/warpscout/core"
)

func TestMobileScanOptionsDefaults(t *testing.T) {
	opts, err := mobileScanOptions(core.ScanOptions{})
	if err != nil {
		t.Fatal(err)
	}
	if opts.proto != protoWG || opts.innerProto != protoWG {
		t.Fatalf("protocols = %q, %q", opts.proto, opts.innerProto)
	}
	if opts.timeoutSec != 2 || opts.tunnelParallel != defaultTunnelJobs || opts.perSubnet != 5 {
		t.Fatalf("defaults = timeout %d, jobs %d, sample %d", opts.timeoutSec, opts.tunnelParallel, opts.perSubnet)
	}
	if awgJc != 6 || awgJmin != 10 || awgJmax != 50 || awgI1 != i1Default {
		t.Fatal("AmneziaWG defaults were not restored")
	}
}

func TestMobileScanOptionsValidatesBoundary(t *testing.T) {
	_, err := mobileScanOptions(core.ScanOptions{TunnelPingCount: minDurabilityPings - 1})
	assertCoreError(t, err, "invalid_tunnel_ping_count")

	_, err = mobileScanOptions(core.ScanOptions{Protocol: core.ProtocolMASQUEH3, IncludeNodes: []string{"FRA"}})
	assertCoreError(t, err, "unsupported_filter")

	opts, err := mobileScanOptions(core.ScanOptions{
		Protocol:        core.ProtocolAWG,
		InnerProtocol:   core.ProtocolWG,
		ThroughEndpoint: "188.114.96.1:2408",
		Jobs:            20,
	})
	if err != nil {
		t.Fatal(err)
	}
	if opts.tunnelParallel != nestedTunnelJobs {
		t.Fatalf("nested jobs = %d", opts.tunnelParallel)
	}
}

func TestMobileTargetSelectsAddressFamily(t *testing.T) {
	opts, err := mobileScanOptions(core.ScanOptions{CustomTarget: "2606:4700:d0::1"})
	if err != nil {
		t.Fatal(err)
	}
	if !opts.ipv6 || len(opts.targets) != 1 {
		t.Fatalf("target options = ipv6 %t, targets %d", opts.ipv6, len(opts.targets))
	}
}

func TestMobileResultsUseCLIOrder(t *testing.T) {
	results := []endpointResult{
		{endpoint: "one", tunPing: 10 * time.Millisecond, loss: 0.2, measured: true, ok: true, durable: true},
		{endpoint: "two", tunPing: 50 * time.Millisecond, loss: 0.1, measured: true, ok: true, durable: true},
		{endpoint: "three", tunPing: 5 * time.Millisecond, measured: true, ok: true, durable: false},
		{endpoint: "failed"},
	}
	got := mobileResults(results)
	if len(got) != 3 {
		t.Fatalf("result count = %d", len(got))
	}
	if got[0].Endpoint != "two" || got[1].Endpoint != "one" || got[2].Endpoint != "three" {
		t.Fatalf("result order = %q, %q, %q", got[0].Endpoint, got[1].Endpoint, got[2].Endpoint)
	}
}

func TestMobileRenderReport(t *testing.T) {
	backend := NewMobileBackend()
	report, err := backend.RenderReport(core.ScanReport{
		Protocol:   core.ProtocolWG,
		StartedAt:  time.Date(2026, 8, 19, 10, 0, 0, 0, time.UTC),
		FinishedAt: time.Date(2026, 8, 19, 10, 1, 0, 0, time.UTC),
		Results: []core.EndpointResult{{
			Endpoint: "188.114.96.1:2408",
			Region:   "DE",
			Node:     "FRA",
			Country:  "DE",
			Working:  true,
			Durable:  true,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	for _, value := range []string{"WARPSCOUT scan report", "Protocol: wg", "188.114.96.1:2408", "working", "FRA"} {
		if !strings.Contains(report, value) {
			t.Fatalf("report does not contain %q", value)
		}
	}
}

func TestMobileRenderConfigFormats(t *testing.T) {
	privateKey, publicKey := warpPrivateKey, warpPublicKey
	addressV4, addressV6 := warpAddress, warpAddressV6
	masque, outerAccount := masqueAcct, outerAcct
	defer func() {
		warpPrivateKey, warpPublicKey = privateKey, publicKey
		warpAddress, warpAddressV6 = addressV4, addressV6
		masqueAcct, outerAcct = masque, outerAccount
	}()

	backend := NewMobileBackend()
	account := core.Account{RawJSON: `{
		"id":"device","token":"token","private_key":"private","peer_public_key":"public",
		"ipv4":"172.16.0.2","ipv6":"2606:4700:110::2",
		"masque":{
			"id":"masque-device","token":"masque-token","private_key":"masque-private",
			"peer_public_key":"-----BEGIN PUBLIC KEY-----\neA==\n-----END PUBLIC KEY-----\n",
			"ipv4":"172.16.0.3","ipv6":"2606:4700:110::3"
		}
	}`}
	endpoint := core.EndpointResult{Endpoint: "188.114.96.1:2408", Working: true, Durable: true}
	tests := []struct {
		name     string
		options  core.ScanOptions
		format   core.ConfigFormat
		contains []string
	}{
		{"wireguard", core.ScanOptions{Protocol: core.ProtocolWG}, core.ConfigWireGuard, []string{"[Interface]", "Endpoint = 188.114.96.1:2408"}},
		{"amneziawg", core.ScanOptions{Protocol: core.ProtocolAWG}, core.ConfigAmneziaWG, []string{"Jc = 6", "Jmin = 10", "Jmax = 50"}},
		{"usque", core.ScanOptions{Protocol: core.ProtocolMASQUEH3}, core.ConfigUSQUE, []string{`"endpoint_v4": "188.114.96.1"`, `"access_token": "masque-token"`}},
		{"mihomo", core.ScanOptions{Protocol: core.ProtocolAWG}, core.ConfigMihomo, []string{"proxies:", "type: wireguard", "amnezia-wg-option:"}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			config, err := backend.RenderConfig(account, endpoint, test.options, test.format)
			if err != nil {
				t.Fatal(err)
			}
			for _, value := range test.contains {
				if !strings.Contains(config, value) {
					t.Fatalf("config does not contain %q:\n%s", value, config)
				}
			}
		})
	}
}

func assertCoreError(t *testing.T, err error, code string) {
	t.Helper()
	typed, ok := err.(*core.CoreError)
	if !ok {
		t.Fatalf("error = %T %v", err, err)
	}
	if typed.Code != code {
		t.Fatalf("error code = %q", typed.Code)
	}
}
