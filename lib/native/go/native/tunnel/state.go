package tunnel

import (
	"strings"

	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

func QueryMode() string {
	return tunnel.Mode().String()
}

func PatchMode(mode string) bool {
	switch strings.ToLower(strings.TrimSpace(mode)) {
	case "rule":
		tunnel.SetMode(tunnel.Rule)
	case "global":
		tunnel.SetMode(tunnel.Global)
	case "direct":
		tunnel.SetMode(tunnel.Direct)
	default:
		log.Warnln("Patch tunnel mode failed: invalid mode %s", mode)
		return false
	}

	log.Infoln("Patch tunnel mode -> %s", tunnel.Mode().String())
	return true
}
