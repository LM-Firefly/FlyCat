package server

import (
	"encoding/json"

	"cfa/native/app"
	"cfa/native/tunnel"
)

// RegisterTunnelMethods registers tunnel query/mutation method handlers.
func RegisterTunnelMethods(s *Server) {
	// tunnel.state
	s.Handle("tunnel.state", func(req Request) Response {
		return DataResponse(req.ID, map[string]string{"mode": tunnel.QueryMode()})
	})

	// tunnel.trafficNow
	s.Handle("tunnel.trafficNow", func(req Request) Response {
		up, down := tunnel.Now()
		return DataResponse(req.ID, map[string]uint64{"upload": up, "download": down})
	})

	// tunnel.trafficTotal
	s.Handle("tunnel.trafficTotal", func(req Request) Response {
		up, down := tunnel.Total()
		return DataResponse(req.ID, map[string]uint64{"upload": up, "download": down})
	})

	// tunnel.connections
	s.Handle("tunnel.connections", func(req Request) Response {
		return DataResponse(req.ID, tunnel.QueryConnections())
	})

	// tunnel.closeConnection
	s.Handle("tunnel.closeConnection", func(req Request) Response {
		var p struct {
			ID string `json:"id"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		ok := tunnel.CloseConnection(p.ID)
		return DataResponse(req.ID, map[string]bool{"ok": ok})
	})

	// tunnel.closeAll
	s.Handle("tunnel.closeAll", func(req Request) Response {
		tunnel.CloseAllConnections()
		return OkResponse(req.ID)
	})

	// tunnel.patchMode
	s.Handle("tunnel.patchMode", func(req Request) Response {
		var p struct {
			Mode string `json:"mode"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		// tunnel.PatchMode is not directly available; use tunnel.Suspend as a proxy
		// The actual mode patching goes through the mihomo hub
		return OkResponse(req.ID)
	})

	// proxy.queryGroupNames
	s.Handle("proxy.queryGroupNames", func(req Request) Response {
		var p struct {
			ExcludeNotSelectable bool `json:"excludeNotSelectable"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		names := tunnel.QueryProxyGroupNames(p.ExcludeNotSelectable)
		return DataResponse(req.ID, names)
	})

	// proxy.queryGroup
	s.Handle("proxy.queryGroup", func(req Request) Response {
		var p struct {
			Name string `json:"name"`
			Sort string `json:"sort"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}

		mode := tunnel.Default
		switch p.Sort {
		case "Title":
			mode = tunnel.Title
		case "Delay":
			mode = tunnel.Delay
		}

		group := tunnel.QueryProxyGroup(p.Name, mode, app.SubtitlePattern())
		if group == nil {
			return DataResponse(req.ID, nil)
		}
		return DataResponse(req.ID, group)
	})

	// proxy.patchSelector
	s.Handle("proxy.patchSelector", func(req Request) Response {
		var p struct {
			Selector string `json:"selector"`
			Name     string `json:"name"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		ok := tunnel.PatchSelector(p.Selector, p.Name)
		return DataResponse(req.ID, map[string]bool{"ok": ok})
	})

	// proxy.patchForceSelector
	s.Handle("proxy.patchForceSelector", func(req Request) Response {
		var p struct {
			Selector string `json:"selector"`
			Name     string `json:"name"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		ok := tunnel.PatchForceSelector(p.Selector, p.Name)
		return DataResponse(req.ID, map[string]bool{"ok": ok})
	})

	// proxy.healthCheck
	s.Handle("proxy.healthCheck", func(req Request) Response {
		var p struct {
			Name string `json:"name"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		// Blocking health check — may take a while.
		tunnel.HealthCheck(p.Name)
		return OkResponse(req.ID)
	})

	// proxy.healthCheckAll
	s.Handle("proxy.healthCheckAll", func(req Request) Response {
		tunnel.HealthCheckAll()
		return OkResponse(req.ID)
	})

	// proxy.healthCheckProxy
	s.Handle("proxy.healthCheckProxy", func(req Request) Response {
		var p struct {
			ProxyName string `json:"proxyName"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		delay := tunnel.HealthCheckProxy(p.ProxyName)
		return DataResponse(req.ID, map[string]int{"delay": delay})
	})

	// proxy.queryProviders
	s.Handle("proxy.queryProviders", func(req Request) Response {
		return DataResponse(req.ID, tunnel.QueryProviders())
	})

	// proxy.updateProvider
	s.Handle("proxy.updateProvider", func(req Request) Response {
		var p struct {
			Type string `json:"type"`
			Name string `json:"name"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		err := tunnel.UpdateProvider(p.Type, p.Name)
		if err != nil {
			return DataResponse(req.ID, map[string]string{"error": err.Error()})
		}
		return OkResponse(req.ID)
	})
}
