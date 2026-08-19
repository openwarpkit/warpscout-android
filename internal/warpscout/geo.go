package warpscout

import "strings"

var showEmoji bool

// The city is the edge node's own, so it can differ from the colo code - HEL
// sits in Vantaa, not Helsinki.
func coloLocation(m metaResult) string {
	if m.coloCity == "" && m.coloISO == "" {
		return "?"
	}
	if m.coloCity == "" {
		return withFlag(flagEmoji(m.coloISO), m.coloISO)
	}
	return withFlag(flagEmoji(m.coloISO), m.coloCity+", "+m.coloISO)
}

const regionalIndicatorA = 0x1F1E6

func flagEmoji(iso string) string {
	if !showEmoji {
		return ""
	}
	if len(iso) != 2 {
		return ""
	}
	var flag strings.Builder
	for _, c := range strings.ToUpper(iso) {
		if c < 'A' || c > 'Z' {
			return ""
		}
		flag.WriteRune(regionalIndicatorA + (c - 'A'))
	}
	return flag.String()
}
