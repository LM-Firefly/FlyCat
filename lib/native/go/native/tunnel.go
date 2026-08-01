package main

//#include "bridge.h"
import "C"

import (
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

	return marshalJSON(response)
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
		if C.connection_close_received(remote, marshalJSON(event)) != 0 {
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

//export subscribeTrafficUpdate
func subscribeTrafficUpdate(remote unsafe.Pointer) {
	previous := swapSubscriber(&trafficUpdateSubscriber, remote)
	if previous != nil && previous != remote {
		C.release_object(previous)
	}

	tunnel.SetTrafficUpdateListener(func(event *tunnel.TrafficUpdateEvent) {
		if C.traffic_update_received(remote, marshalJSON(event)) != 0 {
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

		response := &struct {
			Delay int `json:"delay"`
		}{delay}

		C.complete_with_string(completable, marshalJSON(response))
	}(C.GoString(proxyName))
}

//export patchSelector
func patchSelector(selector, name C.c_string) C.int {
	s := C.GoString(selector)
	n := C.GoString(name)

	if tunnel.PatchSelector(s, n) {
		return 1
	}

	return 0
}

//export patchForceSelector
func patchForceSelector(selector, name C.c_string) C.int {
	s := C.GoString(selector)
	n := C.GoString(name)
	if tunnel.PatchForceSelector(s, n) {
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
