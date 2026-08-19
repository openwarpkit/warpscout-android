package warpscout

import (
	"os"
	"path/filepath"
)

// os.Executable() cannot be used to spot it: under the linker /proc/self/exe is
// linker64 itself, not the binary.
func isLoaderArg(arg, argv0 string) bool {
	if !filepath.IsAbs(arg) || filepath.Base(arg) != filepath.Base(argv0) {
		return false
	}
	_, err := os.Stat(arg)
	return err == nil
}
