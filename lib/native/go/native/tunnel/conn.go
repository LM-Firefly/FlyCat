// Package tunnel exposes proxy groups, connections, providers, and traffic statistics.
package tunnel

import (
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// QueryConnections returns a snapshot of all active connections.
func QueryConnections() *statistic.Snapshot {
	return statistic.DefaultManager.Snapshot()
}

// CloseConnection closes the connection identified by the given ID.
func CloseConnection(id string) bool {
	conn := statistic.DefaultManager.Get(id)
	if conn == nil {
		return false
	}

	return conn.Close() == nil
}

// CloseAllConnections closes all active connections.
func CloseAllConnections() {
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		_ = c.Close()
		return true
	})
}

// ConnectionCloseEvent represents the data emitted when a connection is closed or opened.
type ConnectionCloseEvent struct {
	ID             string   `json:"id"`
	Upload         int64    `json:"upload"`
	Download       int64    `json:"download"`
	Metadata       any      `json:"metadata"`
	Rule           string   `json:"rule"`
	RulePayload    string   `json:"rulePayload"`
	Chains         []string `json:"chains"`
	ProviderChains []string `json:"providerChains"`
}

// SetConnectionLeaveListener registers a callback invoked when a connection is closed.
func SetConnectionLeaveListener(listener func(*ConnectionCloseEvent)) {
	statistic.DefaultManager.OnLeave = func(info *statistic.TrackerInfo) {
		if listener == nil {
			return
		}
		event := &ConnectionCloseEvent{
			ID:             info.UUID.String(),
			Upload:         info.UploadTotal.Load(),
			Download:       info.DownloadTotal.Load(),
			Metadata:       info.Metadata,
			Rule:           info.Rule,
			RulePayload:    info.RulePayload,
			Chains:         info.Chain,
			ProviderChains: info.ProviderChain,
		}
		listener(event)
	}
}

// SetConnectionJoinListener registers a callback invoked when a new connection is established.
func SetConnectionJoinListener(listener func(*ConnectionCloseEvent)) {
	statistic.DefaultManager.OnJoin = func(info *statistic.TrackerInfo) {
		if listener == nil {
			return
		}
		event := &ConnectionCloseEvent{
			ID:             info.UUID.String(),
			Upload:         info.UploadTotal.Load(),
			Download:       info.DownloadTotal.Load(),
			Metadata:       info.Metadata,
			Rule:           info.Rule,
			RulePayload:    info.RulePayload,
			Chains:         info.Chain,
			ProviderChains: info.ProviderChain,
		}
		listener(event)
	}
}

// TrafficUpdateEvent contains periodic upload/download traffic statistics.
type TrafficUpdateEvent struct {
	UploadTotal   int64 `json:"uploadTotal"`
	DownloadTotal int64 `json:"downloadTotal"`
	UploadSpeed   int64 `json:"uploadSpeed"`
	DownloadSpeed int64 `json:"downloadSpeed"`
}

// SetTrafficUpdateListener registers a callback invoked on each traffic statistics tick.
func SetTrafficUpdateListener(listener func(*TrafficUpdateEvent)) {
	statistic.DefaultManager.OnTrafficUpdate = func(uploadTotal, downloadTotal, uploadSpeed, downloadSpeed int64) {
		if listener == nil {
			return
		}
		event := &TrafficUpdateEvent{
			UploadTotal:   uploadTotal,
			DownloadTotal: downloadTotal,
			UploadSpeed:   uploadSpeed,
			DownloadSpeed: downloadSpeed,
		}
		listener(event)
	}
}

func closeMatch(filter func(conn C.Connection) bool) {
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		if filter(c) {
			_ = c.Close()
		}
		return true
	})
}

func closeConnByGroup(name string) {
	closeMatch(func(conn C.Connection) bool {
		for _, c := range conn.Chains() {
			if c == name {
				return true
			}
		}

		return false
	})
}
