//go:build android

package warpscout

import "os"

func stripLoaderArg() {
	if len(os.Args) < 2 || !isLoaderArg(os.Args[1], os.Args[0]) {
		return
	}
	os.Args = append(os.Args[:1], os.Args[2:]...)
}
