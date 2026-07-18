package app

import (
	"net"
	"strconv"
	"strings"
	"syscall"

	"cfa/native/platform"
)

var markSocketImpl func(fd int)
var querySocketOwnerImpl func(protocol int, source, target string) string

// SocketOwner holds the UID and package name of the app that owns a socket.
type SocketOwner struct {
	UID     int
	Package string
}

// MarkSocket applies platform-specific socket marks (e.g. VPN bypass) to the given fd.
func MarkSocket(fd int) {
	markSocketImpl(fd)
}

// QuerySocketOwner returns the UID and package name of the app that owns the source socket.
func QuerySocketOwner(source, target net.Addr) SocketOwner {
	var protocol int

	switch source.Network() {
	case "udp", "udp4", "udp6":
		protocol = syscall.IPPROTO_UDP
	case "tcp", "tcp4", "tcp6":
		protocol = syscall.IPPROTO_TCP
	default:
		return SocketOwner{UID: -1}
	}

	if PlatformVersion() < 29 {
		uid := platform.QuerySocketUIDFromProcFs(source, target)
		return SocketOwner{UID: uid}
	}

	return decodeSocketOwner(querySocketOwnerImpl(protocol, source.String(), target.String()))
}

// ApplyTunContext registers the platform-specific socket marker and owner query implementations.
func ApplyTunContext(
	markSocket func(fd int),
	querySocketOwner func(int, string, string) string,
) {
	if markSocket == nil {
		// Fallback used until the Android layer wires a real protector; ignoring the fd is intentional.
		markSocket = func(_ int) {}
	}

	if querySocketOwner == nil {
		querySocketOwner = func(int, string, string) string { return encodeSocketOwner(SocketOwner{UID: -1}) }
	}

	markSocketImpl = markSocket
	querySocketOwnerImpl = querySocketOwner
}

func init() {
	ApplyTunContext(nil, nil)
}

func encodeSocketOwner(owner SocketOwner) string {
	return strconv.Itoa(owner.UID) + "\t" + owner.Package
}

func decodeSocketOwner(value string) SocketOwner {
	uidPart, pkgPart, found := strings.Cut(value, "\t")
	if !found {
		return SocketOwner{UID: -1}
	}

	uid, err := strconv.Atoi(uidPart)
	if err != nil {
		uid = -1
	}

	return SocketOwner{
		UID:     uid,
		Package: pkgPart,
	}
}
