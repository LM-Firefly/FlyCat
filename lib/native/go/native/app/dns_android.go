//go:build android && cmfa

// Package app provides Android application context and platform utilities for the native bridge.
package app

import (
	"strings"

	"github.com/metacubex/mihomo/dns"
)

// NotifyDNSChanged notifies the system of a DNS server change (Android implementation).
func NotifyDNSChanged(dnsList string) {
	var addr []string
	if len(dnsList) > 0 {
		addr = strings.Split(dnsList, ",")
	}
	dns.UpdateSystemDNS(addr)
	dns.FlushCacheWithDefaultResolver()
}
