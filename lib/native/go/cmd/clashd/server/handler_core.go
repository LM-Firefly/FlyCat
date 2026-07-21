package server

import (
	"encoding/json"
	"runtime"
	"runtime/debug"

	"cfa/native/config"
	"cfa/native/delegate"
	"cfa/native/tunnel"

	"github.com/metacubex/mihomo/log"
)

// RegisterCoreMethods registers core lifecycle method handlers on the server.
func RegisterCoreMethods(s *Server) {
	// core.init — initialises the mihomo engine.
	s.Handle("core.init", func(req Request) Response {
		var p struct {
			Home        string `json:"home"`
			VersionName string `json:"versionName"`
			GitVersion  string `json:"gitVersion"`
			SDKVersion  int    `json:"sdkVersion"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}

		delegate.Init(p.Home, p.VersionName, p.GitVersion, p.SDKVersion)

		// reset after init (mirrors nativeInit in main.go)
		config.LoadDefault()
		tunnel.ResetStatistic()
		tunnel.CloseAllConnections()
		runtime.GC()
		debug.FreeOSMemory()

		log.Infoln("[UDS] core.init ok — home=%s version=%s", p.Home, p.VersionName)
		return OkResponse(req.ID)
	})

	// core.reset — reloads defaults and frees memory.
	s.Handle("core.reset", func(req Request) Response {
		config.LoadDefault()
		tunnel.ResetStatistic()
		tunnel.CloseAllConnections()
		runtime.GC()
		debug.FreeOSMemory()
		return OkResponse(req.ID)
	})

	// core.forceGc — triggers garbage collection.
	s.Handle("core.forceGc", func(req Request) Response {
		go func() {
			log.Infoln("[APP] request force GC")
			runtime.GC()
			debug.FreeOSMemory()
		}()
		return OkResponse(req.ID)
	})

	// core.suspend — suspends or resumes the tunnel.
	s.Handle("core.suspend", func(req Request) Response {
		var p struct {
			Suspended bool `json:"suspended"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		tunnel.Suspend(p.Suspended)
		return OkResponse(req.ID)
	})

	// core.version — returns the core version string.
	s.Handle("core.version", func(req Request) Response {
		type ver struct {
			Version string `json:"version"`
		}
		return DataResponse(req.ID, ver{Version: "uds-dev"})
	})

	// core.setUserAgent — sets a custom User-Agent.
	s.Handle("core.setUserAgent", func(req Request) Response {
		var p struct {
			UserAgent string `json:"userAgent"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		config.SetCustomUserAgent(p.UserAgent)
		log.Infoln("[APP] custom User-Agent set:", p.UserAgent)
		return OkResponse(req.ID)
	})

	// core.setAgeSecretKey — sets the age encryption secret key.
	s.Handle("core.setAgeSecretKey", func(req Request) Response {
		var p struct {
			Key string `json:"key"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		if p.Key == "" {
			config.SetGlobalSecretKeys()
		} else {
			config.SetGlobalSecretKeys(p.Key)
		}
		return OkResponse(req.ID)
	})

	// core.ping — simple health check.
	s.Handle("core.ping", func(req Request) Response {
		return DataResponse(req.ID, map[string]string{"pong": "ok"})
	})
}
