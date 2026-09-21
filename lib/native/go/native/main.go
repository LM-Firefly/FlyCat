// Package main implements the native bridge for the FlyCat Android application.
package main

/*
#cgo LDFLAGS: -llog

#include "bridge.h"
*/
import "C"

import (
	"runtime"
	"runtime/debug"

	"cfa/native/config"
	"cfa/native/delegate"
	"cfa/native/tunnel"

	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
)

func main() {
	panic("Stub!")
}

//export coreInit
func coreInit(home, versionName, gitVersion C.c_string, sdkVersion C.int) {
	h := C.GoString(home)
	v := C.GoString(versionName)
	g := C.GoString(gitVersion)
	s := int(sdkVersion)

	// 安卓的 RSS 上限。仅 GOMEMLIMIT 就足以防止堆不断膨胀；请不要在此处将 GOGC 收紧到 100 以下——较低的 GOGC 会以电池/CPU 为代价换取内存，且实测在后台表现更差。
	// 512 MiB 为地理数据/DNS 缓存留出了余量。
	debug.SetMemoryLimit(512 << 20) // 512 MiB
	debug.SetGCPercent(100)         // keep default pacing; let GOMEMLIMIT do the RSS work

	delegate.Init(h, v, g, s)

	reset()
}

//export reset
func reset() {
	config.LoadDefault()
	tunnel.ResetStatistic()
	tunnel.CloseAllConnections()

	runtime.GC()
	debug.FreeOSMemory()
}

//export forceGc
func forceGc() {
	go func() {
		log.Infoln("[APP] request force GC")

		runtime.GC()
		debug.FreeOSMemory()
	}()
}

//export setMemoryLimit
func setMemoryLimit(bytes C.int64_t) {
	limit := int64(bytes)
	if limit <= 0 {
		limit = 512 << 20 // default 512 MiB
	}
	old := debug.SetMemoryLimit(limit)
	log.Infoln("[APP] GOMEMLIMIT %d MiB -> %d MiB", old>>20, limit>>20)
}

//export setCustomUserAgent
func setCustomUserAgent(userAgent C.c_string) {
	ua := C.GoString(userAgent)
	config.SetCustomUserAgent(ua)
	log.Infoln("[APP] custom User-Agent set:", ua)
}

//export queryCoreVersion
func queryCoreVersion() *C.char {
	return C.CString(constant.Version)
}
