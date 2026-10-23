// Package main implements the native bridge for the FlyCat Android application.
package main

//#include "bridge.h"
import "C"

import (
	"strconv"
	"sync"
	"time"
	"unsafe"

	"github.com/metacubex/mihomo/log"
)

type message struct {
	Level   string `json:"level"`
	Message string `json:"message"`
	Time    int64  `json:"time"`
}

//export subscribeLogcat
func subscribeLogcat(remote unsafe.Pointer) {
	previous, previousStop, stopCh := replaceLogcatSubscriber(remote)
	if previousStop != nil {
		close(previousStop)
	}
	if previous != nil && previous != remote {
		C.release_object(previous)
	}
	go func(remote unsafe.Pointer, stop <-chan struct{}) {
		sub := log.Subscribe()
		defer log.UnSubscribe(sub)
		for {
			select {
			case <-stop:
				return
			case msg, ok := <-sub:
				if !ok {
					return
				}
				// Emit the full log stream (every level) regardless of the kernel's
				// configured log-level. The mihomo log bus broadcasts unconditionally;
				// the level gate used to be applied here which made the in-app preview
				// unable to show Debug/Info when the core level was higher. Level
				// filtering is now a client-side concern (real-time filter in the UI),
				// so the profile's log-level / override no longer constrains what the
				// user can see in the live log preview.
				rMsg := &message{
					Level:   msg.LogLevel.String(),
					Message: msg.Payload,
					Time:    time.Now().UnixNano() / 1000 / 1000,
				}
				// Fast delimiter payload (level \x01 time \x01 message) avoids the per-message
				// json.Marshal reflection/escaping cost; Kotlin parses it back without a JSON decode.
				buf := make([]byte, 0, len(rMsg.Level)+len(rMsg.Message)+24)
				buf = append(buf, rMsg.Level...)
				buf = append(buf, 0x01)
				buf = strconv.AppendInt(buf, rMsg.Time, 10)
				buf = append(buf, 0x01)
				buf = append(buf, rMsg.Message...)
				if C.logcat_received(remote, cStringFromBytes(buf)) != 0 {
					released, matched := clearLogcatSubscriberIfCurrent(remote, stopCh)
					if matched && released != nil {
						C.release_object(released)
					}
					log.Debugln("Logcat subscriber closed")
					return
				}
			}
		}
	}(remote, stopCh)
	log.Infoln("[APP] Logcat level: %s", log.Level().String())
}

//export unsubscribeLogcat
func unsubscribeLogcat() {
	released, stop := clearLogcatSubscriber()
	if stop != nil {
		close(stop)
	}
	if released != nil {
		C.release_object(released)
	}
}

var (
	logcatSubscriptionMu sync.Mutex
	logcatSubscriber     unsafe.Pointer
	logcatStopCh         chan struct{}
)

func replaceLogcatSubscriber(next unsafe.Pointer) (previous unsafe.Pointer, previousStop chan struct{}, currentStop chan struct{}) {
	logcatSubscriptionMu.Lock()
	previous = logcatSubscriber
	previousStop = logcatStopCh
	logcatSubscriber = next
	if next != nil {
		currentStop = make(chan struct{})
		logcatStopCh = currentStop
	} else {
		logcatStopCh = nil
	}
	logcatSubscriptionMu.Unlock()
	return
}

func clearLogcatSubscriber() (released unsafe.Pointer, stop chan struct{}) {
	logcatSubscriptionMu.Lock()
	released = logcatSubscriber
	stop = logcatStopCh
	logcatSubscriber = nil
	logcatStopCh = nil
	logcatSubscriptionMu.Unlock()
	return
}

func clearLogcatSubscriberIfCurrent(expected unsafe.Pointer, expectedStop chan struct{}) (released unsafe.Pointer, matched bool) {
	logcatSubscriptionMu.Lock()
	if logcatSubscriber == expected && logcatStopCh == expectedStop {
		released = logcatSubscriber
		logcatSubscriber = nil
		logcatStopCh = nil
		matched = true
	}
	logcatSubscriptionMu.Unlock()
	return
}
