//go:build !android || !cmfa

// Package app provides Android application context and platform utilities for the native bridge.
package app

// NotifyDNSChanged is a no-op stub for non-Android platforms.
func NotifyDNSChanged(dnsList string) {
	_ = dnsList
}
