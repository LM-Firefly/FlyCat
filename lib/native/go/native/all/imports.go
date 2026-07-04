package all

import (
	// Blank imports aggregate every cfa bridge package so building this single
	// package compiles the whole native bridge surface (init side effects
	// included), even the packages only referenced by the cgo main package.
	_ "cfa/native/app"
	_ "cfa/native/common"
	_ "cfa/native/config"
	_ "cfa/native/delegate"
	_ "cfa/native/platform"
	_ "cfa/native/proxy"
	_ "cfa/native/tun"
	_ "cfa/native/tunnel"

	// Mirrors the cgo main package's semaphore dependency (native/tun.go) so it
	// stays a direct requirement of this module's importable package graph.
	_ "golang.org/x/sync/semaphore"

	// Links the mihomo logging pipeline the bridge subscribes to at runtime.
	_ "github.com/metacubex/mihomo/log"
)
