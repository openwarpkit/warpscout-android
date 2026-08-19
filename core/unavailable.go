package core

import "context"

type UnavailableBackend struct{}

func NewUnavailableBackend() UnavailableBackend {
	return UnavailableBackend{}
}

func (UnavailableBackend) Register(context.Context, RegisterOptions, EventSink) (Account, error) {
	return Account{}, unavailable()
}

func (UnavailableBackend) Scan(context.Context, Account, ScanOptions, EventSink) (ScanReport, error) {
	return ScanReport{}, unavailable()
}

func (UnavailableBackend) FindJunk(context.Context, Account, ScanOptions, EventSink) (JunkProfile, error) {
	return JunkProfile{}, unavailable()
}

func (UnavailableBackend) FindSNI(context.Context, Account, ScanOptions, EventSink) (SNIProfile, error) {
	return SNIProfile{}, unavailable()
}

func (UnavailableBackend) StartSocks(context.Context, Account, SocksOptions, EventSink) error {
	return unavailable()
}

func (UnavailableBackend) RenderReport(ScanReport) (string, error) {
	return "", unavailable()
}

func (UnavailableBackend) RenderConfig(Account, EndpointResult, ScanOptions, ConfigFormat) (string, error) {
	return "", unavailable()
}

func unavailable() error {
	return &CoreError{Code: "core_unavailable", Message: "the WARPSCOUT backend is not linked into this build"}
}
