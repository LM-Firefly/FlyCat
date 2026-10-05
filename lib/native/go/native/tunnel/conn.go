// Package tunnel exposes proxy groups, connections, providers, and traffic statistics.
package tunnel

import (
	"sync"
	"sync/atomic"
	"time"

	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// connectionGeneration is a monotonic counter incremented on every join/close event.
// Pollers check this before doing expensive full queries to skip unchanged snapshots.
var connectionGeneration atomic.Uint64

// ConnectionGeneration returns the current generation counter value.
// A change since the last poll means the connection set has changed.
func ConnectionGeneration() uint64 {
	return connectionGeneration.Load()
}

// IncrConnectionGeneration increments the connection generation counter.
func IncrConnectionGeneration() {
	connectionGeneration.Add(1)
}

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

// ConnectionCloseEvent 携带已关闭连接的动态计数器和匹配结果。
// 完整的元数据被省略（在加入时已交付；它是该高周转路径上最大的字段）。规则/链被保留，因为匹配可能在加入之后才最终确定。
// 包/进程/uid 被保留作为精简身份标识，以便缓存未命中的关闭事件在没有元数据块的情况下仍可归因。
type ConnectionCloseEvent struct {
	ID             string   `json:"id"`
	Upload         int64    `json:"upload"`
	Download       int64    `json:"download"`
	UploadDelta    int64    `json:"uploadDelta"`
	DownloadDelta  int64    `json:"downloadDelta"`
	Rule           string   `json:"rule"`
	RulePayload    string   `json:"rulePayload"`
	Chains         []string `json:"chains"`
	ProviderChains []string `json:"providerChains"`
	PackageName    string   `json:"packageName,omitempty"`
	ProcessName    string   `json:"process,omitempty"`
	UID            int64    `json:"uid,omitempty"`
}

// fillCloseIdentity 仅从追踪器元数据中提取 packageName/process/uid，因此缓存未命中关闭时仍可归属，而无需附带完整的元数据块。
func fillCloseIdentity(event *ConnectionCloseEvent, meta any) {
	type identityMeta interface {
		GetProcess() string
		GetUid() int64
	}
	switch m := meta.(type) {
	case *C.Metadata:
		if m == nil {
			return
		}
		event.ProcessName = m.Process
		if m.Uid != 0 {
			event.UID = int64(m.Uid)
		}
		// Android 下 Process 已被 FindPackageName 覆写为包名，回填 PackageName 供 pkg: 归因桶使用。
		if m.Process != "" {
			event.PackageName = m.Process
		}
	case map[string]any:
		if v, ok := m["packageName"].(string); ok {
			event.PackageName = v
		}
		if v, ok := m["process"].(string); ok {
			event.ProcessName = v
		}
		switch u := m["uid"].(type) {
		case float64:
			event.UID = int64(u)
		case int64:
			event.UID = u
		case int:
			event.UID = int64(u)
		}
	default:
		if im, ok := meta.(identityMeta); ok {
			event.ProcessName = im.GetProcess()
			if uid := im.GetUid(); uid != 0 {
				event.UID = uid
			}
		}
	}
}

// closeEventThrottler 在快速连接关闭事件跨越 JNI 之前将其合并。
// 大规模关闭（选择器切换、配置重载）加上高变动流量可能连续触发数百个 OnLeave 回调。每一个都会成为一次 JNI 回调；Rust 端的队列是有界的（1024），会被填满，此后 join/close 回退到在 Go goroutine 上进行同步 JNI 调用。
// 加上在 patchSelector 线程上同步执行 closeConnByGroup（现已移至后台 goroutine），这使得节点选择看起来像是挂死了。
type closeEventThrottler struct {
	mu       sync.Mutex
	pending  map[string]*ConnectionCloseEvent
	order    []string
	timer    *time.Timer
	listener func(*ConnectionCloseEvent)
}

const (
	closeEventFlushInterval = 100 * time.Millisecond
	// 当合并批次足够大时，及早刷新，因为继续等待已无意义。
	closeEventFlushBatch = 128
)

var leaveThrottler closeEventThrottler

func (t *closeEventThrottler) offer(event *ConnectionCloseEvent) {
	t.mu.Lock()
	if t.pending == nil {
		t.pending = make(map[string]*ConnectionCloseEvent, closeEventFlushBatch)
	}
	if _, exists := t.pending[event.ID]; !exists {
		t.order = append(t.order, event.ID)
	}
	t.pending[event.ID] = event
	shouldFlushNow := len(t.order) >= closeEventFlushBatch
	if shouldFlushNow {
		if t.timer != nil {
			t.timer.Stop()
			t.timer = nil
		}
	} else if t.timer == nil {
		t.timer = time.AfterFunc(closeEventFlushInterval, t.flush)
	}
	t.mu.Unlock()
	if shouldFlushNow {
		t.flush()
	}
}

func (t *closeEventThrottler) flush() {
	t.mu.Lock()
	t.timer = nil
	pending := t.pending
	order := t.order
	listener := t.listener
	t.pending = nil
	t.order = nil
	t.mu.Unlock()
	if listener == nil {
		return
	}
	for _, id := range order {
		if event, ok := pending[id]; ok {
			listener(event)
		}
	}
}

func (t *closeEventThrottler) setListener(listener func(*ConnectionCloseEvent)) {
	t.mu.Lock()
	t.listener = listener
	t.mu.Unlock()
}

// drain 立即冲刷任何缓冲事件（监听器拆卸/取消订阅）。
func (t *closeEventThrottler) drain() {
	t.mu.Lock()
	if t.timer != nil {
		t.timer.Stop()
		t.timer = nil
	}
	t.mu.Unlock()
	t.flush()
}

// SetConnectionLeaveListener 注册一个在连接关闭时被调用的回调。
// 事件在跨越 JNI 边界之前会被合并（参见 closeEventThrottler）。
func SetConnectionLeaveListener(listener func(*ConnectionCloseEvent)) {
	if listener == nil {
		leaveThrottler.drain()
		leaveThrottler.setListener(nil)
		statistic.DefaultManager.OnLeave = nil
		return
	}
	leaveThrottler.setListener(listener)
	statistic.DefaultManager.OnLeave = func(info *statistic.TrackerInfo) {
		event := &ConnectionCloseEvent{
			ID:             info.UUID.String(),
			Upload:         info.UploadTotal.Load(),
			Download:       info.DownloadTotal.Load(),
			UploadDelta:    info.UploadTotal.Load() - info.InitialUpload,
			DownloadDelta:  info.DownloadTotal.Load() - info.InitialDownload,
			Rule:           info.Rule,
			RulePayload:    info.RulePayload,
			Chains:         info.Chain,
			ProviderChains: info.ProviderChain,
		}
		fillCloseIdentity(event, info.Metadata)
		leaveThrottler.offer(event)
	}
}

// ClearConnectionLeaveListener 会注销连接关闭回调。
func ClearConnectionLeaveListener() {
	leaveThrottler.drain()
	leaveThrottler.setListener(nil)
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
