// Package tunnel exposes proxy groups, connections, providers, and traffic statistics.
package tunnel

import (
	"time"

	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// QueryConnections returns a snapshot of all active connections.
func QueryConnections() *statistic.Snapshot {
	return statistic.DefaultManager.Snapshot()
}

// ConnectionOverview contains only the dynamic per-connection counters needed for list refreshes.
type ConnectionOverview struct {
	ID       string `json:"id"`
	Upload   int64  `json:"upload"`
	Download int64  `json:"download"`
}

// ConnectionOverviewSnapshot is a lightweight snapshot for connection list polling.
type ConnectionOverviewSnapshot struct {
	DownloadTotal int64                 `json:"downloadTotal"`
	UploadTotal   int64                 `json:"uploadTotal"`
	Connections   []*ConnectionOverview `json:"connections"`
	Memory        uint64                `json:"memory"`
}

// QueryConnectionsOverview returns only connection ids and counters for lightweight polling.
func QueryConnectionsOverview() *ConnectionOverviewSnapshot {
	var connections []*ConnectionOverview
	statistic.DefaultManager.Range(func(c statistic.Tracker) bool {
		info := c.Info()
		connections = append(connections, &ConnectionOverview{
			ID:       info.UUID.String(),
			Upload:   info.UploadTotal.Load(),
			Download: info.DownloadTotal.Load(),
		})
		return true
	})
	up, down := statistic.DefaultManager.Total()
	return &ConnectionOverviewSnapshot{
		UploadTotal:   up,
		DownloadTotal: down,
		Connections:   connections,
	}
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

// ConnectionJoinEvent carries the static details needed when a connection first appears.
type ConnectionJoinEvent struct {
	ID             string    `json:"id"`
	Start          time.Time `json:"start"`
	Metadata       any       `json:"metadata"`
	Rule           string    `json:"rule"`
	RulePayload    string    `json:"rulePayload"`
	Chains         []string  `json:"chains"`
	ProviderChains []string  `json:"providerChains"`
}

// ConnectionCloseEvent carries only the dynamic counters needed when a connection closes.
type ConnectionCloseEvent struct {
	ID       string `json:"id"`
	Upload   int64  `json:"upload"`
	Download int64  `json:"download"`
}

// SetConnectionLeaveListener registers a callback invoked when a connection is closed.
func SetConnectionLeaveListener(listener func(*ConnectionCloseEvent)) {
	if listener == nil {
		statistic.DefaultManager.OnLeave = nil
		return
	}

	statistic.DefaultManager.OnLeave = func(info *statistic.TrackerInfo) {
		event := &ConnectionCloseEvent{
			ID:       info.UUID.String(),
			Upload:   info.UploadTotal.Load(),
			Download: info.DownloadTotal.Load(),
		}
		listener(event)
	}
}

// ClearConnectionLeaveListener unregisters the connection-close callback.
func ClearConnectionLeaveListener() {
	statistic.DefaultManager.OnLeave = nil
}

// SetConnectionJoinListener registers a callback invoked when a new connection is established.
func SetConnectionJoinListener(listener func(*ConnectionJoinEvent)) {
	if listener == nil {
		statistic.DefaultManager.OnJoin = nil
		return
	}

	statistic.DefaultManager.OnJoin = func(info *statistic.TrackerInfo) {
		event := &ConnectionJoinEvent{
			ID:             info.UUID.String(),
			Start:          info.Start,
			Metadata:       info.Metadata,
			Rule:           info.Rule,
			RulePayload:    info.RulePayload,
			Chains:         info.Chain,
			ProviderChains: info.ProviderChain,
		}
		listener(event)
	}
}

// ClearConnectionJoinListener unregisters the connection-join callback.
func ClearConnectionJoinListener() {
	statistic.DefaultManager.OnJoin = nil
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
	if listener == nil {
		statistic.DefaultManager.OnTrafficUpdate = nil
		return
	}

	statistic.DefaultManager.OnTrafficUpdate = func(uploadTotal, downloadTotal, uploadSpeed, downloadSpeed int64) {
		event := &TrafficUpdateEvent{
			UploadTotal:   uploadTotal,
			DownloadTotal: downloadTotal,
			UploadSpeed:   uploadSpeed,
			DownloadSpeed: downloadSpeed,
		}
		listener(event)
	}
}

// ClearTrafficUpdateListener unregisters the traffic-update callback.
func ClearTrafficUpdateListener() {
	statistic.DefaultManager.OnTrafficUpdate = nil
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
