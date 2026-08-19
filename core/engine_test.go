package core

import (
	"context"
	"sync"
	"testing"
)

type blockingBackend struct {
	started sync.Once
	ready   chan struct{}
}

func (b *blockingBackend) Register(context.Context, RegisterOptions, EventSink) (Account, error) {
	return Account{}, nil
}

func (b *blockingBackend) Scan(ctx context.Context, _ Account, _ ScanOptions, _ EventSink) (ScanReport, error) {
	b.started.Do(func() { close(b.ready) })
	<-ctx.Done()
	return ScanReport{}, ctx.Err()
}

func (b *blockingBackend) FindJunk(context.Context, Account, ScanOptions, EventSink) (JunkProfile, error) {
	return JunkProfile{}, nil
}

func (b *blockingBackend) FindSNI(context.Context, Account, ScanOptions, EventSink) (SNIProfile, error) {
	return SNIProfile{}, nil
}

func (b *blockingBackend) StartSocks(context.Context, Account, SocksOptions, EventSink) error {
	return nil
}

func (b *blockingBackend) RenderReport(ScanReport) (string, error) {
	return "", nil
}

func (b *blockingBackend) RenderConfig(Account, EndpointResult, ScanOptions, ConfigFormat) (string, error) {
	return "", nil
}

func TestEngineRejectsConcurrentOperationAndCancels(t *testing.T) {
	backend := &blockingBackend{ready: make(chan struct{})}
	engine := New(backend)
	done := make(chan error, 1)
	go func() {
		_, err := engine.Scan(context.Background(), Account{}, ScanOptions{}, nil)
		done <- err
	}()
	<-backend.ready

	_, err := engine.Scan(context.Background(), Account{}, ScanOptions{}, nil)
	coreError, ok := err.(*CoreError)
	if !ok || coreError.Code != "operation_active" {
		t.Fatalf("expected operation_active, got %v", err)
	}

	engine.Cancel()
	if err := <-done; err != context.Canceled {
		t.Fatalf("expected cancellation, got %v", err)
	}
}
