package core

import (
	"context"
	"sync"
)

type EventSink func(ProgressEvent)

type Backend interface {
	Register(context.Context, RegisterOptions, EventSink) (Account, error)
	Scan(context.Context, Account, ScanOptions, EventSink) (ScanReport, error)
	FindJunk(context.Context, Account, ScanOptions, EventSink) (JunkProfile, error)
	FindSNI(context.Context, Account, ScanOptions, EventSink) (SNIProfile, error)
	StartSocks(context.Context, Account, SocksOptions, EventSink) error
	RenderReport(ScanReport) (string, error)
	RenderConfig(Account, EndpointResult, ScanOptions, ConfigFormat) (string, error)
}

type Engine struct {
	backend Backend
	mu      sync.Mutex
	cancel  context.CancelFunc
}

func New(backend Backend) *Engine {
	return &Engine{backend: backend}
}

func (e *Engine) Register(ctx context.Context, options RegisterOptions, sink EventSink) (Account, error) {
	var account Account
	err := e.run(ctx, func(operationContext context.Context) error {
		var err error
		account, err = e.backend.Register(operationContext, options, sink)
		return err
	})
	return account, err
}

func (e *Engine) Scan(ctx context.Context, account Account, options ScanOptions, sink EventSink) (ScanReport, error) {
	var report ScanReport
	err := e.run(ctx, func(operationContext context.Context) error {
		var err error
		report, err = e.backend.Scan(operationContext, account, options, sink)
		return err
	})
	return report, err
}

func (e *Engine) FindJunk(ctx context.Context, account Account, options ScanOptions, sink EventSink) (JunkProfile, error) {
	var profile JunkProfile
	err := e.run(ctx, func(operationContext context.Context) error {
		var err error
		profile, err = e.backend.FindJunk(operationContext, account, options, sink)
		return err
	})
	return profile, err
}

func (e *Engine) FindSNI(ctx context.Context, account Account, options ScanOptions, sink EventSink) (SNIProfile, error) {
	var profile SNIProfile
	err := e.run(ctx, func(operationContext context.Context) error {
		var err error
		profile, err = e.backend.FindSNI(operationContext, account, options, sink)
		return err
	})
	return profile, err
}

func (e *Engine) StartSocks(ctx context.Context, account Account, options SocksOptions, sink EventSink) error {
	if options.Port < 1 || options.Port > 65535 {
		return &CoreError{Code: "invalid_port", Message: "SOCKS port must be between 1 and 65535"}
	}
	return e.run(ctx, func(operationContext context.Context) error {
		return e.backend.StartSocks(operationContext, account, options, sink)
	})
}

func (e *Engine) RenderReport(report ScanReport) (string, error) {
	return e.backend.RenderReport(report)
}

func (e *Engine) RenderConfig(account Account, endpoint EndpointResult, options ScanOptions, format ConfigFormat) (string, error) {
	return e.backend.RenderConfig(account, endpoint, options, format)
}

func (e *Engine) Cancel() {
	e.mu.Lock()
	cancel := e.cancel
	e.mu.Unlock()
	if cancel != nil {
		cancel()
	}
}

func (e *Engine) run(ctx context.Context, operation func(context.Context) error) error {
	e.mu.Lock()
	if e.cancel != nil {
		e.mu.Unlock()
		return &CoreError{Code: "operation_active", Message: "another operation is already active", Retryable: true}
	}
	operationContext, cancel := context.WithCancel(ctx)
	e.cancel = cancel
	e.mu.Unlock()

	defer func() {
		cancel()
		e.mu.Lock()
		e.cancel = nil
		e.mu.Unlock()
	}()

	return operation(operationContext)
}
