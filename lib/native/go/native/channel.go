package main

import (
	"bytes"
	"fmt"
	"os"
	"strconv"
	"sync"
	"syscall"
)

// The client half of the launcher socketpair: config + TUN fd inbound at startup, socket-owner
// RPC for the rest of the session. The other half is CoreProcess.kt.
const (
	setupBufSize      = 64 * 1024 // >= the launcher's write chunk (CHUNK)
	ownerReplyBufSize = 4096      // == the launcher's reply buffer (OWNER_QUERY_BUFFER_SIZE)
)

// readSetup drains the config; the launcher terminates it with the TUN fd via SCM_RIGHTS.
func readSetup(channelFd int) ([]byte, int, error) {
	payload := make([]byte, 0, setupBufSize)
	buf := make([]byte, setupBufSize)
	oob := make([]byte, syscall.CmsgSpace(4)) // room for exactly one descriptor
	for {
		n, oobn, flags, _, err := syscall.Recvmsg(channelFd, buf, oob, 0)
		if err != nil {
			return nil, -1, err
		}
		// SEQPACKET discards what does not fit; MSG_CTRUNC means the TUN fd itself is gone.
		if flags&(syscall.MSG_TRUNC|syscall.MSG_CTRUNC) != 0 {
			return nil, -1, fmt.Errorf("setup datagram truncated (flags=%#x, %d of %d bytes)", flags, n, len(buf))
		}
		if fd := parseRightsFd(oob[:oobn]); fd >= 0 {
			return payload, fd, nil
		}
		if n == 0 {
			// The fd only arrives last, so EOF first means the handoff broke; treating that as
			// success produced a core with no tun and a default config.
			return nil, -1, fmt.Errorf("channel closed after %d config bytes, before the tun fd arrived", len(payload))
		}
		payload = append(payload, buf[:n]...)
	}
}

// socketOwnerQuery asks the launcher which app owns a connection, over the socketpair the config
// arrived on; nil in the root modes, which have no launcher to ask. Wire format: request
// "<ipproto>\t<src>\t<dst>", reply "<uid>\t<package>", "-1\t" = unknown.
func socketOwnerQuery(channelFd int) ownerQuery {
	if channelFd < 0 {
		return nil
	}

	// The mutex serialises every call, which is what makes the scratch buffers reusable.
	var (
		mutex   sync.Mutex
		broken  bool
		logOnce sync.Once

		request = make([]byte, 0, 128)
		reply   = make([]byte, ownerReplyBufSize)
	)

	report := func(err error, n int) {
		logOnce.Do(func() {
			fmt.Fprintf(os.Stderr, "socket-owner RPC failed (err=%v n=%d); per-app rules fall back to /proc/net\n", err, n)
		})
	}

	return func(protocol int, source, target string) (int, string) {
		mutex.Lock()
		defer mutex.Unlock()
		if broken {
			return -1, ""
		}

		request = strconv.AppendInt(request[:0], int64(protocol), 10)
		request = append(request, '\t')
		request = append(request, source...)
		request = append(request, '\t')
		request = append(request, target...)

		if err := syscall.Sendmsg(channelFd, request, nil, nil, 0); err != nil {
			// SEQPACKET sendmsg is all-or-nothing: still aligned, so do not latch the RPC off.
			report(err, 0)
			return -1, ""
		}

		for {
			n, _, _, _, err := syscall.Recvmsg(channelFd, reply, nil, 0)
			if err == syscall.EINTR {
				continue // delivery is atomic: nothing consumed, still aligned
			}
			if err != nil || n <= 0 {
				// The launcher queues one reply per request, so asking again would stay one
				// reply behind forever and blame every connection on the previous app.
				broken = true
				report(err, n)
				return -1, ""
			}
			return decodeSocketOwner(reply[:n])
		}
	}
}

func decodeSocketOwner(reply []byte) (int, string) {
	uidPart, pkg, found := bytes.Cut(reply, []byte{'\t'})
	if !found {
		return -1, ""
	}
	uid, err := strconv.Atoi(string(uidPart))
	if err != nil {
		uid = -1 // an unparsable uid still yields the package, if any
	}
	return uid, string(pkg)
}

func parseRightsFd(oob []byte) int {
	if len(oob) == 0 {
		return -1
	}
	messages, err := syscall.ParseSocketControlMessage(oob)
	if err != nil {
		return -1
	}
	for i := range messages {
		if fds, err := syscall.ParseUnixRights(&messages[i]); err == nil && len(fds) > 0 {
			return fds[0]
		}
	}
	return -1
}
