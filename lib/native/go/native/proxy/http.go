// Package proxy manages the local HTTP proxy listener.
package proxy

import (
	"sync"

	"github.com/metacubex/mihomo/listener/http"
	"github.com/metacubex/mihomo/tunnel"
)

var listener *http.Listener
var lock sync.Mutex

// Start starts a local HTTP proxy listener on the given address.
func Start(listen string) (listenAt string, err error) {
	lock.Lock()
	defer lock.Unlock()

	stopLocked()

	listener, err = http.NewWithAuthenticate(listen, tunnel.Tunnel, false)
	if err == nil {
		listenAt = listener.Address()
	}

	return
}

// Stop stops the running HTTP proxy listener.
func Stop() {
	lock.Lock()
	defer lock.Unlock()

	stopLocked()
}

func stopLocked() {
	if listener != nil {
		listener.Close()
	}

	listener = nil
}
