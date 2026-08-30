// Package main implements the native bridge for the FlyCat Android application.
package main

//#include "bridge.h"
import "C"

import (
	"strings"
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
				if msg.LogLevel < log.Level() && !strings.HasPrefix(msg.Payload, "[APP]") {
					continue
				}
				rMsg := &message{
					Level:   msg.LogLevel.String(),
					Message: msg.Payload,
					Time:    time.Now().UnixNano() / 1000 / 1000,
				}
				if C.logcat_received(remote, marshalJSON(rMsg)) != 0 {
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
