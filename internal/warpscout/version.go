package warpscout

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime/debug"
	"strconv"
	"strings"
	"time"
)

const (
	ghLatestURL = "https://api.github.com/repos/vernette/warpscout/releases/latest"

	updateCheckTimeout = 2 * time.Second
	updateCacheTTL     = 6 * time.Hour
	updateCacheName    = "warpscout-latest-version"
)

// Set through -ldflags using the full package path from the release tag.
var version = "dev"

func runVersionCmd(context.Context, options) error {
	fmt.Println(resolveVersion())
	return nil
}

func resolveVersion() string {
	if version != "dev" {
		return version
	}
	// "go install ...@latest" embeds the module version; a plain "go build" gives "(devel)".
	if bi, ok := debug.ReadBuildInfo(); ok && bi.Main.Version != "" && bi.Main.Version != "(devel)" {
		return strings.TrimPrefix(bi.Main.Version, "v")
	}
	return version
}

func noticeUpdate(ctx context.Context) {
	cur := resolveVersion()
	latest := latestVersion(ctx)
	if !newerVersion(latest, cur) {
		return
	}
	writeUpdateBanner(os.Stderr, newConStyles(consoleRenderer(os.Stderr)), cur, latest)
}

func writeUpdateBanner(w io.Writer, st conStyles, cur, latest string) {
	line := st.warn.Bold(true).Render("Update available") +
		st.dim.Render(": "+cur+" → ") + st.accent.Bold(true).Render(latest)
	fmt.Fprintln(w, st.box.Render(line))
	fmt.Fprintln(w)
}

// An empty answer means "unknown" - the caller stays quiet rather than failing a
// scan over a version check.
func latestVersion(ctx context.Context) string {
	path := updateCachePath()
	if v, ok := readVersionCache(path, updateCacheTTL); ok {
		return v
	}
	v := fetchLatestVersion(ctx)
	// Cached even when the fetch failed: on a network where GitHub is blocked
	// every run would otherwise pay the timeout.
	writeVersionCache(path, v)
	return v
}

// In the temp dir rather than os.UserCacheDir(): on OpenWrt that is /root/.cache
// on the flash overlay, and this file is disposable. The uid keeps a shared /tmp
// from letting the first user own it and every other one silently fail to write.
func updateCachePath() string {
	return filepath.Join(os.TempDir(), fmt.Sprintf("%s-%d", updateCacheName, os.Getuid()))
}

func readVersionCache(path string, ttl time.Duration) (string, bool) {
	if path == "" || ttl <= 0 {
		return "", false
	}
	fi, err := os.Stat(path)
	if err != nil || time.Since(fi.ModTime()) > ttl {
		return "", false
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return "", false
	}
	return strings.TrimSpace(string(data)), true
}

func writeVersionCache(path, version string) {
	if path == "" {
		return
	}
	os.WriteFile(path, []byte(version+"\n"), 0644)
}

func fetchLatestVersion(ctx context.Context) string {
	ctx, cancel := context.WithTimeout(ctx, updateCheckTimeout)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, ghLatestURL, nil)
	if err != nil {
		return ""
	}
	req.Header.Set("Accept", "application/vnd.github+json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return ""
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return ""
	}
	var r struct {
		Tag string `json:"tag_name"`
	}
	if json.NewDecoder(resp.Body).Decode(&r) != nil {
		return ""
	}
	return strings.TrimPrefix(r.Tag, "v")
}

func newerVersion(latest, current string) bool {
	l, ok := parseSemver(latest)
	if !ok {
		return false
	}
	c, ok := parseSemver(current)
	if !ok {
		return false
	}
	for i := range l {
		if l[i] != c[i] {
			return l[i] > c[i]
		}
	}
	return false
}

func parseSemver(v string) ([3]int, bool) {
	var out [3]int
	parts := strings.Split(strings.TrimPrefix(v, "v"), ".")
	if len(parts) != len(out) {
		return out, false
	}
	for i, p := range parts {
		n, err := strconv.Atoi(p)
		if err != nil || n < 0 {
			return out, false
		}
		out[i] = n
	}
	return out, true
}
