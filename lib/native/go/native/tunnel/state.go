package tunnel

import (
	"github.com/metacubex/mihomo/tunnel"
)

// QueryMode returns the current tunnel mode as a string.
func QueryMode() string {
	return tunnel.Mode().String()
}

// PatchTunnelMode sets the tunnel mode. Returns false if the mode is invalid.
func PatchTunnelMode(mode string) bool {
	m, ok := tunnel.ModeMapping[mode]
	if !ok {
		return false
	}
	tunnel.SetMode(m)
	return true
}
