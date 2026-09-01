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
