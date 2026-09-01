package mobileapi

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"

	"github.com/vernette/warpscout/core"
	"github.com/vernette/warpscout/internal/warpscout"
)

var (
	coreVersion     = "dev"
	upstreamVersion = "v0.16.0"
	engineMu        sync.RWMutex
	engine          = core.New(warpscout.NewMobileBackend())
)

type Listener interface {
	OnEvent(eventJSON string)
}

type request struct {
	SchemaVersion int             `json:"schemaVersion"`
	Operation     core.Operation  `json:"operation"`
	AccountJSON   string          `json:"accountJson,omitempty"`
	Payload       json.RawMessage `json:"payload"`
}

type responseEvent struct {
	SchemaVersion int         `json:"schemaVersion"`
	Type          string      `json:"type"`
	Operation     string      `json:"operation,omitempty"`
	Payload       any         `json:"payload,omitempty"`
	Error         *errorEvent `json:"error,omitempty"`
}

type errorEvent struct {
	Code      string `json:"code"`
	Message   string `json:"message"`
	Retryable bool   `json:"retryable"`
	Payload   any    `json:"payload,omitempty"`
}

type reportRequest struct {
	Report core.ScanReport `json:"report"`
}

type configRequest struct {
	Endpoint core.EndpointResult `json:"endpoint"`
	Options  core.ScanOptions    `json:"options"`
	Format   core.ConfigFormat   `json:"format"`
}

func Start(requestJSON string, listener Listener) (err error) {
	defer func() {
		if recovered := recover(); recovered != nil {
			err = fmt.Errorf("core panic: %v", recovered)
			emitError(listener, "core_panic", "the core stopped unexpectedly", false)
		}
	}()

	var command request
	if err := json.Unmarshal([]byte(requestJSON), &command); err != nil {
		emitError(listener, "invalid_request", "request JSON is invalid", false)
		return err
	}
	if command.SchemaVersion != 1 {
		err := fmt.Errorf("unsupported schema version %d", command.SchemaVersion)
		emitError(listener, "unsupported_schema", err.Error(), false)
		return err
	}

	engineMu.RLock()
	activeEngine := engine
	engineMu.RUnlock()
	sink := func(event core.ProgressEvent) {
		event.SchemaVersion = 1
		emit(listener, event)
	}
	account := core.Account{RawJSON: command.AccountJSON}
	ctx := context.Background()

	var result any
	switch command.Operation {
	case core.OperationRegister:
		var options core.RegisterOptions
		if err := json.Unmarshal(command.Payload, &options); err != nil {
			return invalidPayload(listener, err)
		}
		result, err = activeEngine.Register(ctx, options, sink)
	case core.OperationScan:
		var options core.ScanOptions
		if err := json.Unmarshal(command.Payload, &options); err != nil {
			return invalidPayload(listener, err)
		}
		result, err = activeEngine.Scan(ctx, account, options, sink)
	case core.OperationFindJunk:
		var options core.ScanOptions
		if err := json.Unmarshal(command.Payload, &options); err != nil {
			return invalidPayload(listener, err)
		}
		result, err = activeEngine.FindJunk(ctx, account, options, sink)
	case core.OperationFindSNI:
		var options core.ScanOptions
		if err := json.Unmarshal(command.Payload, &options); err != nil {
			return invalidPayload(listener, err)
		}
		result, err = activeEngine.FindSNI(ctx, account, options, sink)
	case core.OperationSocks:
		var options core.SocksOptions
		if err := json.Unmarshal(command.Payload, &options); err != nil {
			return invalidPayload(listener, err)
		}
		err = activeEngine.StartSocks(ctx, account, options, sink)
		result = map[string]bool{"stopped": err == nil}
	case core.OperationReport:
		var input reportRequest
		if err := json.Unmarshal(command.Payload, &input); err != nil {
			return invalidPayload(listener, err)
		}
		result, err = activeEngine.RenderReport(input.Report)
	case core.OperationConfig:
		var input configRequest
		if err := json.Unmarshal(command.Payload, &input); err != nil {
			return invalidPayload(listener, err)
		}
		result, err = activeEngine.RenderConfig(account, input.Endpoint, input.Options, input.Format)
	default:
		err = fmt.Errorf("unknown operation %q", command.Operation)
		emitError(listener, "unknown_operation", err.Error(), false)
		return err
	}

	if err != nil {
		emitCoreError(listener, err)
		return err
	}
	emit(listener, responseEvent{SchemaVersion: 1, Type: "completed", Operation: string(command.Operation), Payload: result})
	return nil
}

func Cancel() {
	engineMu.RLock()
	activeEngine := engine
	engineMu.RUnlock()
	activeEngine.Cancel()
}

func Stop() {
	Cancel()
}

func CoreVersion() string {
	return coreVersion
}

func UpstreamVersion() string {
	return upstreamVersion
}

func GenerateI1(host string) (string, error) {
	return warpscout.GenerateI1(host)
}

func invalidPayload(listener Listener, err error) error {
	emitError(listener, "invalid_payload", "operation payload is invalid", false)
	return err
}

func emitCoreError(listener Listener, err error) {
	if typed, ok := err.(*core.CoreError); ok {
		emit(listener, responseEvent{
			SchemaVersion: 1,
			Type:          "error",
			Error: &errorEvent{
				Code:      typed.Code,
				Message:   typed.Message,
				Retryable: typed.Retryable,
				Payload:   typed.Payload,
			},
		})
		return
	}
	emitError(listener, "operation_failed", err.Error(), false)
}

func emitError(listener Listener, code, message string, retryable bool) {
	emit(listener, responseEvent{
		SchemaVersion: 1,
		Type:          "error",
		Error:         &errorEvent{Code: code, Message: message, Retryable: retryable},
	})
}

func emit(listener Listener, value any) {
	if listener == nil {
		return
	}
	data, err := json.Marshal(value)
	if err == nil {
		listener.OnEvent(string(data))
	}
}
