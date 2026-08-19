package warpscout

import (
	"os"

	"github.com/charmbracelet/lipgloss"
	"github.com/muesli/termenv"
)

const (
	ansiReset  = "\033[0m"
	ansiBold   = "\033[1m"
	ansiDim    = "\033[2m"
	ansiRed    = "\033[31m"
	ansiGreen  = "\033[32m"
	ansiCyan   = "\033[36m"
	ansiYellow = "\033[33m"
)

var errPal palette

func isTerminal(f *os.File) bool {
	fi, err := f.Stat()
	if err != nil {
		return false
	}
	return fi.Mode()&os.ModeCharDevice != 0
}

func colorEnabled(f *os.File) bool {
	return os.Getenv("NO_COLOR") == "" && isTerminal(f)
}

func colorForced() bool {
	forced := os.Getenv("CLICOLOR_FORCE")
	return os.Getenv("NO_COLOR") == "" && forced != "" && forced != "0"
}

func consoleRenderer(f *os.File) *lipgloss.Renderer {
	r := lipgloss.NewRenderer(f)
	if r.ColorProfile() == termenv.Ascii && colorForced() {
		r.SetColorProfile(termenv.ANSI256)
	}
	return r
}

type palette struct {
	enabled bool
}

func (p palette) paint(code, s string) string {
	if !p.enabled {
		return s
	}
	return code + s + ansiReset
}

func (p palette) title(s string) string  { return p.paint(ansiBold, s) }
func (p palette) ok(s string) string     { return p.paint(ansiGreen, s) }
func (p palette) fail(s string) string   { return p.paint(ansiRed, s) }
func (p palette) dim(s string) string    { return p.paint(ansiDim, s) }
func (p palette) warn(s string) string   { return p.paint(ansiYellow, s) }
func (p palette) accent(s string) string { return p.paint(ansiCyan, s) }
func (p palette) addr(s string) string   { return p.paint(ansiBold, s) }
