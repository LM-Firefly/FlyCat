//go:build debug
// +build debug

// Package main implements the native bridge for the FlyCat Android application.
package main

import (
	"net/http"
	_ "net/http/pprof"

	"github.com/metacubex/mihomo/log"
)

func init() {
	go func() {
		log.Debugln("pprof service listen at: 127.0.0.1:8888")

		// nosemgrep: go.lang.security.audit.net.listenAndServe-tls.UseOfHttpInsteadOfHttps
		// localhost-only debug pprof endpoint; TLS not applicable for loopback.
		_ = http.ListenAndServe("127.0.0.1:8888", nil)
	}()
}
