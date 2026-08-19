//go:build !windows

package warpscout

import "github.com/charmbracelet/bubbles/spinner"

const (
	glyphOK   = "✔"
	glyphFail = "✗"
)

var scanSpinner = spinner.Dot

func enableVirtualTerminal() {}
