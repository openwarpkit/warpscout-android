package mobileapi

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/vernette/warpscout/core"
)

func TestGenerateI1(t *testing.T) {
	value, err := GenerateI1("www.example.com")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(value, "<b 0x") || !strings.HasSuffix(value, ">") {
		t.Fatalf("GenerateI1() = %q", value)
	}
}

type captureListener struct {
	value string
}

func (listener *captureListener) OnEvent(value string) {
	listener.value = value
}

func TestRenderJSONReportFromHistory(t *testing.T) {
	listener := &captureListener{}
	err := Start(`{
		"schemaVersion": 1,
		"operation": "render-report",
		"payload": {
			"format": "json",
			"options": {"tunnelPingCount": 10},
			"report": {
				"protocol": "awg",
				"testedCount": 70,
				"results": [
					{"endpoint":"[2001:db8::1]:2408","node":"FRA","country":"DE","nodeLocation":"Frankfurt, DE","working":true,"durable":true,"tunnelPingMs":15.25,"speedMbps":30},
					{"endpoint":"192.0.2.2:2408","node":"FRA","working":true,"durable":true,"tunnelPingMs":10,"speedMbps":12},
					{"endpoint":"192.0.2.3:2408","working":true,"durable":false,"lossPercent":100}
				]
			}
		}
	}`, listener)
	if err != nil {
		t.Fatal(err)
	}
	var event struct {
		Type    string `json:"type"`
		Payload string `json:"payload"`
	}
	if err := json.Unmarshal([]byte(listener.value), &event); err != nil {
		t.Fatal(err)
	}
	if event.Type != "completed" {
		t.Fatalf("unexpected event: %+v", event)
	}
	type endpoint struct {
		Endpoint string   `json:"endpoint"`
		IP       string   `json:"ip"`
		Port     int      `json:"port"`
		NodeCity string   `json:"node_city"`
		PingMS   *float64 `json:"ping_ms"`
		LossPct  *float64 `json:"loss_pct"`
		Torn     bool     `json:"torn"`
	}
	var report struct {
		Schema       string     `json:"$schema"`
		Version      int        `json:"version"`
		Proto        string     `json:"proto"`
		Total        int        `json:"total"`
		WorkingCount int        `json:"working_count"`
		Best         string     `json:"best"`
		Picks        []endpoint `json:"picks"`
		Working      []endpoint `json:"working"`
		Torn         []endpoint `json:"torn"`
	}
	if err := json.Unmarshal([]byte(event.Payload), &report); err != nil {
		t.Fatal(err)
	}
	if !strings.HasSuffix(report.Schema, "/schema/warpscout-v1.schema.json") ||
		report.Version != 1 || report.Proto != "awg" || report.Total != 70 || report.WorkingCount != 2 {
		t.Fatalf("unexpected report: %+v", report)
	}
	if len(report.Picks) != 1 || len(report.Working) != 2 || len(report.Torn) != 1 {
		t.Fatalf("unexpected endpoint groups: %+v", report)
	}
	best := report.Working[0]
	if report.Best != "[2001:db8::1]:2408" || report.Picks[0].Endpoint != report.Best ||
		best.IP != "2001:db8::1" || best.Port != 2408 || best.NodeCity != "Frankfurt" ||
		best.PingMS != nil || best.LossPct == nil || *best.LossPct != 0 {
		t.Fatalf("unexpected best endpoint: %+v", best)
	}
	if torn := report.Torn[0]; !torn.Torn || torn.LossPct == nil || *torn.LossPct != 100 {
		t.Fatalf("unexpected torn endpoint: %+v", torn)
	}
}

func TestCoreErrorIncludesPartialPayload(t *testing.T) {
	listener := &captureListener{}
	emitCoreError(listener, &core.CoreError{
		Code:      "sni_not_found",
		Message:   "not found",
		Retryable: true,
		Payload: core.SNIProfile{
			Protocol: core.ProtocolMASQUEH3,
			Tested:   []core.SNIAttempt{{SNI: "example.com", Completed: true}},
		},
	})

	var event struct {
		Error struct {
			Payload core.SNIProfile `json:"payload"`
		} `json:"error"`
	}
	if err := json.Unmarshal([]byte(listener.value), &event); err != nil {
		t.Fatal(err)
	}
	if len(event.Error.Payload.Tested) != 1 || event.Error.Payload.Tested[0].SNI != "example.com" {
		t.Fatalf("unexpected error payload: %+v", event.Error.Payload)
	}
}
