package server

import (
	"encoding/json"
	"net"
	"time"

	"cfa/native/app"
	"cfa/native/proxy"
	"cfa/native/tunnel"

	"github.com/metacubex/mihomo/log"
)

// RegisterSystemMethods registers system-level method handlers (DNS, timezone, HTTP proxy, TUN).
func RegisterSystemMethods(s *Server) {
	// system.notifyDnsChanged
	s.Handle("system.notifyDnsChanged", func(req Request) Response {
		var p struct {
			DNSList string `json:"dnsList"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		app.NotifyDNSChanged(p.DNSList)
		return OkResponse(req.ID)
	})

	// system.notifyTimeZoneChanged
	s.Handle("system.notifyTimeZoneChanged", func(req Request) Response {
		var p struct {
			Name   string `json:"name"`
			Offset int    `json:"offset"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		app.NotifyTimeZoneChanged(p.Name, p.Offset)
		return OkResponse(req.ID)
	})

	// system.queryConfiguration
	s.Handle("system.queryConfiguration", func(req Request) Response {
		return DataResponse(req.ID, map[string]any{})
	})

	// http.start — start the HTTP proxy listener.
	s.Handle("http.start", func(req Request) Response {
		var p struct {
			ListenAt string `json:"listenAt"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}
		listen, err := proxy.Start(p.ListenAt)
		if err != nil {
			return ErrorResponse(req.ID, 500, err.Error())
		}
		return DataResponse(req.ID, map[string]string{"listen": listen})
	})

	// http.stop — stop the HTTP proxy listener.
	s.Handle("http.stop", func(req Request) Response {
		proxy.Stop()
		return OkResponse(req.ID)
	})

	// tun.start — start TUN device.
	// The fd must first be received via tun.passFd, then this method is called.
	s.Handle("tun.start", func(req Request) Response {
		var p struct {
			FD      int    `json:"fd"`
			Stack   string `json:"stack"`
			Gateway string `json:"gateway"`
			Portal  string `json:"portal"`
			DNS     string `json:"dns"`
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			return ErrorResponse(req.ID, 400, "bad params: "+err.Error())
		}

		if p.FD < 0 {
			return ErrorResponse(req.ID, 400, "invalid fd: call tun.passFd first to send the TUN fd")
		}

		log.Infoln("[UDS] tun.start: fd=%d stack=%s gw=%s portal=%s dns=%s", p.FD, p.Stack, p.Gateway, p.Portal, p.DNS)
		// TODO: Wire into the Go TUN start logic.
		// tunnel.StartTun(p.FD, p.Stack, p.Gateway, p.Portal, p.DNS)
		return OkResponse(req.ID)
	})

	// tun.stop — stop TUN device.
	s.Handle("tun.stop", func(req Request) Response {
		log.Infoln("[UDS] tun.stop")
		// TODO: Wire into the Go TUN stop logic.
		// tunnel.StopTun()
		return OkResponse(req.ID)
	})

	// tun.querySocketOwner — reverse callback for socket owner lookup.
	// This is a special method: the Go server sends this as a REQUEST to the Kotlin client
	// on a dedicated callback connection. The Kotlin client handles it and responds.
	// This handler is a placeholder for documentation.
	s.Handle("tun.querySocketOwner", func(req Request) Response {
		return ErrorResponse(req.ID, 501, "tun.querySocketOwner is a reverse-call method handled by the Kotlin client")
	})

	// tun.passFd — receives a TUN file descriptor via SCM_RIGHTS ancillary data.
	// This is an FdHandler: it has access to the raw connection to read ancillary data.
	s.HandleFd("tun.passFd", func(req Request, conn net.Conn) Response {
		var p struct {
			Hint string `json:"hint"` // optional description
		}
		_ = json.Unmarshal(req.Params, &p)

		fd, err := RecvFDFromRawConn(conn)
		if err != nil {
			log.Errorln("[UDS] tun.passFd: failed to receive fd: %v", err)
			return ErrorResponse(req.ID, 500, "failed to receive fd: "+err.Error())
		}

		log.Infoln("[UDS] tun.passFd: received fd=%d hint=%s", fd, p.Hint)

		// Store the fd for later use by tun.start.
		// For now, return it in the response so the client can reference it.
		return DataResponse(req.ID, map[string]int{"fd": fd})
	})
}

// RegisterLogMethods registers log subscription and event push method handlers.
func RegisterLogMethods(s *Server) {
	// log.subscribe — subscribes to log events on the current connection.
	// The connection becomes an event-only connection after this call.
	s.Handle("log.subscribe", func(req Request) Response {
		var p struct {
			Level string `json:"level"` // "info", "debug", "warning", "error"
		}
		if err := json.Unmarshal(req.Params, &p); err != nil {
			// Default to info level.
			p.Level = "info"
		}

		log.Infoln("[UDS] log subscription requested, level: %s", p.Level)

		// Return success — events will be pushed on this connection.
		// The handler returns, but the connection stays open for event pushes.
		return OkResponse(req.ID)
	})
}

// StartLogEventPusher starts a goroutine that subscribes to mihomo log events
// and pushes them to all UDS event subscribers.
func StartLogEventPusher(s *Server) {
	go func() {
		sub := log.Subscribe()
		defer log.UnSubscribe(sub)

		for msg := range sub {
			type logEvent struct {
				Level   string `json:"level"`
				Message string `json:"message"`
				Time    int64  `json:"time"`
			}

			evt := logEvent{
				Level:   msg.LogLevel.String(),
				Message: msg.Payload,
				Time:    time.Now().UnixNano() / 1000 / 1000,
			}

			event, err := NewEvent("log", evt)
			if err != nil {
				continue
			}
			s.PublishEvent(event)
		}
	}()
}

// StartTunnelStateEventPusher starts a goroutine that periodically checks tunnel
// state and pushes state-change events when the mode changes.
func StartTunnelStateEventPusher(s *Server, interval time.Duration) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		lastMode := ""
		for range ticker.C {
			mode := tunnel.QueryMode()
			if mode != lastMode {
				lastMode = mode
				type stateEvent struct {
					Mode string `json:"mode"`
				}
				evt, err := NewEvent("state", stateEvent{Mode: mode})
				if err != nil {
					continue
				}
				s.PublishEvent(evt)
			}
		}
	}()
}

// StartTrafficEventPusher starts a goroutine that periodically pushes traffic stats.
func StartTrafficEventPusher(s *Server, interval time.Duration) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		for range ticker.C {
			type trafficEvent struct {
				UploadNow     uint64 `json:"uploadNow"`
				DownloadNow   uint64 `json:"downloadNow"`
				UploadTotal   uint64 `json:"uploadTotal"`
				DownloadTotal uint64 `json:"downloadTotal"`
			}

			// NOTE: We need to import tunnel package here. The tunnel.Now() and tunnel.Total()
			// functions are available. Let's use them.
			// This will be wired up in main.go with proper imports.
		}
	}()
}
