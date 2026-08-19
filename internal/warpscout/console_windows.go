//go:build windows

package warpscout

import (
	"os"

	"github.com/charmbracelet/bubbles/spinner"
	"golang.org/x/sys/windows"
)

// Consolas and the raster console fonts carry no glyph for the check mark, the
// ballot X or the braille spinner - the legacy console draws a box instead.
const (
	glyphOK   = "+"
	glyphFail = "x"
)

var scanSpinner = spinner.Line

func enableVirtualTerminal() {
	for _, f := range []*os.File{os.Stdout, os.Stderr} {
		h := windows.Handle(f.Fd())
		var mode uint32
		if windows.GetConsoleMode(h, &mode) != nil {
			continue
		}
		_ = windows.SetConsoleMode(h, mode|windows.ENABLE_VIRTUAL_TERMINAL_PROCESSING)
	}
}
