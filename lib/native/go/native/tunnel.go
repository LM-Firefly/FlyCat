// Package main implements the native bridge for the FlyCat Android application.
package main

//#include "bridge.h"
import "C"

import (
	"encoding/json"
	"strconv"
	"strings"
	"sync"
	"unsafe"

	"cfa/native/app"
	"cfa/native/config"
	"cfa/native/tunnel"
)

var (
	subscriptionMu            sync.Mutex
	connectionCloseSubscriber unsafe.Pointer
	connectionJoinSubscriber  unsafe.Pointer
	trafficUpdateSubscriber   unsafe.Pointer
)

func swapSubscriber(slot *unsafe.Pointer, next unsafe.Pointer) (previous unsafe.Pointer) {
	subscriptionMu.Lock()
	previous = *slot
	*slot = next
	subscriptionMu.Unlock()
	return
}

func clearSubscriber(slot *unsafe.Pointer) (released unsafe.Pointer) {
	subscriptionMu.Lock()
	released = *slot
	*slot = nil
	subscriptionMu.Unlock()
	return
}

func clearSubscriberIfCurrent(slot *unsafe.Pointer, expected unsafe.Pointer) (released unsafe.Pointer, matched bool) {
	subscriptionMu.Lock()
	if *slot == expected {
		released = *slot
		*slot = nil
		matched = true
	}
	subscriptionMu.Unlock()
	return
}

//export queryTunnelState
func queryTunnelState() *C.char {
	mode := tunnel.QueryMode()

	response := &struct {
		Mode string `json:"mode"`
	}{mode}

	return marshalJSON(response)
}

//export queryNow
func queryNow(upload, download *C.uint64_t) {
	up, down := tunnel.Now()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryTotal
func queryTotal(upload, download *C.uint64_t) {
	up, down := tunnel.Total()

	*upload = C.uint64_t(up)
	*download = C.uint64_t(down)
}

//export queryConnections
func queryConnections() *C.char {
	return marshalJSON(tunnel.QueryConnections())
}

//export queryConnectionsOverview
func queryConnectionsOverview() *C.char {
	return marshalJSON(tunnel.QueryConnectionsOverview())
}

//export queryConnectionGeneration
func queryConnectionGeneration() C.uint64_t {
	return C.uint64_t(tunnel.ConnectionGeneration())
}

//export queryProxyGroupVersion
func queryProxyGroupVersion() C.uint64_t {
	return C.uint64_t(tunnel.ProxyGroupVersion())
}

//export queryRules
func queryRules() *C.char {
	return marshalJSON(tunnel.QueryRules())
}

//export setRuleDisabled
func setRuleDisabled(index C.int, disabled C.int) C.int {
	if tunnel.SetRuleDisabled(int(index), disabled != 0) {
		return 1
	}
	return 0
}

//export closeConnection
func closeConnection(id C.c_string) C.int {
	if tunnel.CloseConnection(C.GoString(id)) {
		return 1
	}

	return 0
}

//export closeAllConnections
func closeAllConnections() {
	tunnel.CloseAllConnections()
}

//export queryGroupNames
func queryGroupNames(excludeNotSelectable C.int) *C.char {
	return marshalJSON(tunnel.QueryProxyGroupNames(excludeNotSelectable != 0))
}

//export queryGroup
func queryGroup(name C.c_string, sortMode C.c_string) *C.char {
	n := C.GoString(name)
	s := C.GoString(sortMode)

	mode := tunnel.Default

	switch s {
	case "Title":
		mode = tunnel.Title
	case "Delay":
		mode = tunnel.Delay
	}

	response := tunnel.QueryProxyGroup(n, mode, app.SubtitlePattern())

	if response == nil {
		return nil
	}

	// 复用批量载荷的分隔符布局（单组），Kotlin 侧共用同一解码器。
	return encodeProxyGroupsDelimiter([]*tunnel.ProxyGroup{response})
}

//export queryGroupsBatch
func queryGroupsBatch(namesJSON *C.char, sortMode C.c_string) *C.char {
	var nameList []string
	if err := json.Unmarshal([]byte(C.GoString(namesJSON)), &nameList); err != nil {
		return nil
	}

	mode := tunnel.Default
	switch C.GoString(sortMode) {
	case "Title":
		mode = tunnel.Title
	case "Delay":
		mode = tunnel.Delay
	}

	return encodeProxyGroupsDelimiter(tunnel.QueryProxyGroupsBatch(nameList, mode, app.SubtitlePattern()))
}

// encodeProxyGroupsDelimiter 将代理组编码为紧凑的分隔符载荷（组 \x03、代理 \x02、字段 \x01），而不是 JSON。
// 这是应用中最大的载荷（所有组 × 所有节点），因此放弃 json.Marshal 的反射/转义而采用手动布局是值得的。
// 单组查询（queryGroup）同样复用本布局。
func encodeProxyGroupsDelimiter(groups []*tunnel.ProxyGroup) *C.char {
	var sb []byte
	for gi, g := range groups {
		if gi > 0 {
			sb = append(sb, 0x03)
		}
		sb = append(sb, g.Name...)
		sb = append(sb, 0x01)
		sb = append(sb, g.Type...)
		sb = append(sb, 0x01)
		sb = append(sb, g.Now...)
		sb = append(sb, 0x01)
		sb = append(sb, g.Icon...)
		sb = append(sb, 0x01)
		if g.Hidden {
			sb = append(sb, '1')
		} else {
			sb = append(sb, '0')
		}
		sb = append(sb, 0x01)
		sb = append(sb, g.Fixed...)
		sb = append(sb, 0x01)
		for pi, p := range g.Proxies {
			if pi > 0 {
				sb = append(sb, 0x02)
			}
			sb = append(sb, p.Name...)
			sb = append(sb, 0x01)
			sb = append(sb, p.Title...)
			sb = append(sb, 0x01)
			sb = append(sb, p.Subtitle...)
			sb = append(sb, 0x01)
			sb = append(sb, p.Type...)
			sb = append(sb, 0x01)
			sb = strconv.AppendInt(sb, int64(p.Delay), 10)
			sb = append(sb, 0x01)
			if p.IsGroup {
				sb = append(sb, '1')
			} else {
				sb = append(sb, '0')
			}
		}
	}
	return cStringFromBytes(sb)
}

//export healthCheck
func healthCheck(completable unsafe.Pointer, name C.c_string) {
	go func(name string) {
		tunnel.HealthCheck(name)

		C.complete(completable, nil)
	}(C.GoString(name))
}

//export healthCheckAll
func healthCheckAll() {
	tunnel.HealthCheckAll()
}

//export subscribeConnectionClose
func subscribeConnectionClose(remote unsafe.Pointer) {
	previous := swapSubscriber(&connectionCloseSubscriber, remote)
	if previous != nil && previous != remote {
		C.release_object(previous)
	}

	tunnel.SetConnectionLeaveListener(func(event *tunnel.ConnectionCloseEvent) {
		tunnel.IncrConnectionGeneration()
		// 快速分隔符负载（12个以\x01分隔的字段；chains/providerChains之间以\x02连接）避免了在该高频路径上每个事件都进行json.Marshal的反射/转义开销。
		uidStr := ""
		if event.UID != 0 {
			uidStr = strconv.FormatInt(event.UID, 10)
		}
		payload := strings.Join([]string{
			event.ID,
			strconv.FormatInt(event.Upload, 10),
			strconv.FormatInt(event.Download, 10),
			strconv.FormatInt(event.UploadDelta, 10),
			strconv.FormatInt(event.DownloadDelta, 10),
			event.Rule,
			event.RulePayload,
			strings.Join(event.Chains, "\x02"),
			strings.Join(event.ProviderChains, "\x02"),
			event.PackageName,
			event.ProcessName,
			uidStr,
		}, "\x01")
		if C.connection_close_received(remote, cStringFromBytes([]byte(payload))) != 0 {
			released, matched := clearSubscriberIfCurrent(&connectionCloseSubscriber, remote)
			if matched {
				tunnel.ClearConnectionLeaveListener()
			}
			if released != nil {
				C.release_object(released)
			}
		}
	})
}

//export unsubscribeConnectionClose
func unsubscribeConnectionClose() {
	released := clearSubscriber(&connectionCloseSubscriber)
	tunnel.ClearConnectionLeaveListener()
	if released != nil {
		C.release_object(released)
	}
}

//export subscribeConnectionJoin
func subscribeConnectionJoin(remote unsafe.Pointer) {
	previous := swapSubscriber(&connectionJoinSubscriber, remote)
	if previous != nil && previous != remote {
		C.release_object(previous)
	}

	tunnel.SetConnectionJoinListener(func(event *tunnel.ConnectionJoinEvent) {
		tunnel.IncrConnectionGeneration()
		if C.connection_join_received(remote, marshalJSON(event)) != 0 {
			released, matched := clearSubscriberIfCurrent(&connectionJoinSubscriber, remote)
			if matched {
				tunnel.ClearConnectionJoinListener()
			}
			if released != nil {
				C.release_object(released)
			}
		}
	})
}

//export unsubscribeConnectionJoin
func unsubscribeConnectionJoin() {
	released := clearSubscriber(&connectionJoinSubscriber)
	tunnel.ClearConnectionJoinListener()
	if released != nil {
		C.release_object(released)
	}
}

//export subscribeTrafficUpdatePacked
func subscribeTrafficUpdatePacked(remote unsafe.Pointer) {
	previous := swapSubscriber(&trafficUpdateSubscriber, remote)
	if previous != nil && previous != remote {
		C.release_object(previous)
	}

	tunnel.SetTrafficUpdateListener(func(event *tunnel.TrafficUpdateEvent) {
		if C.traffic_update_received_packed(
			remote,
			C.longlong(event.UploadTotal),
			C.longlong(event.DownloadTotal),
			C.longlong(event.UploadSpeed),
			C.longlong(event.DownloadSpeed),
		) != 0 {
			released, matched := clearSubscriberIfCurrent(&trafficUpdateSubscriber, remote)
			if matched {
				tunnel.ClearTrafficUpdateListener()
			}
			if released != nil {
				C.release_object(released)
			}
		}
	})
}

//export unsubscribeTrafficUpdate
func unsubscribeTrafficUpdate() {
	released := clearSubscriber(&trafficUpdateSubscriber)
	tunnel.ClearTrafficUpdateListener()
	if released != nil {
		C.release_object(released)
	}
}

//export healthCheckProxy
func healthCheckProxy(completable unsafe.Pointer, proxyName C.c_string) {
	go func(name string) {
		delay := tunnel.HealthCheckProxy(name)
		// Bare int payload (was {"delay":N} JSON) avoids a per-call json.Marshal on the batch-delay-test hot path (N nodes × one round-trip each).
		C.complete_with_string(completable, C.CString(strconv.Itoa(delay)))
	}(C.GoString(proxyName))
}

//export patchSelector
func patchSelector(selector, name C.c_string) C.int {
	s := C.GoString(selector)
	n := C.GoString(name)

	if tunnel.PatchSelector(s, n) {
		tunnel.IncrProxyGroupVersion()
		return 1
	}

	return 0
}

//export patchForceSelector
func patchForceSelector(selector, name C.c_string) C.int {
	s := C.GoString(selector)
	n := C.GoString(name)
	if tunnel.PatchForceSelector(s, n) {
		tunnel.IncrProxyGroupVersion()
		return 1
	}
	return 0
}

//export queryProviders
func queryProviders() *C.char {
	return marshalJSON(tunnel.QueryProviders())
}

//export updateProvider
func updateProvider(completable unsafe.Pointer, pType C.c_string, name C.c_string) {
	go func(pType, name string) {
		C.complete(completable, marshalString(tunnel.UpdateProvider(pType, name)))

		C.release_object(completable)
	}(C.GoString(pType), C.GoString(name))
}

//export patchTunnelMode
func patchTunnelMode(mode C.c_string) C.int {
	if tunnel.PatchTunnelMode(C.GoString(mode)) {
		return 1
	}
	return 0
}

//export convertMrsToText
func convertMrsToText(filePath C.c_string) *C.char {
	text, err := config.ConvertMrsToText(C.GoString(filePath))
	if err != nil {
		return nil
	}
	return C.CString(text)
}
