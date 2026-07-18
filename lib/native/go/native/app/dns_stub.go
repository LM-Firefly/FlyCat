//go:build !android || !cmfa

package app

// NotifyDNSChanged is a no-op stub for non-Android platforms.
func NotifyDNSChanged(dnsList string) {
	_ = dnsList
}
