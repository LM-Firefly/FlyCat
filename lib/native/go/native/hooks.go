package main

import (
	"net"
	"syscall"

	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
)

// ownerQuery resolves a connection to (uid, package), or (-1, "") when unknown; nil in root modes.
type ownerQuery func(protocol int, source, target string) (uid int, pkg string)

// installHooks points mihomo's process-global hooks at this host.
func installHooks(sdk int, query ownerQuery) {
	process.DefaultPackageNameResolver = func(metadata *constant.Metadata) (string, error) {
		source := metadata.RawSrcAddr
		if source == nil {
			return "", process.ErrInvalidNetwork
		}

		uid, pkg := socketOwner(sdk, query, source, metadata.RawDstAddr)
		if uid >= 0 {
			metadata.Uid = uint32(uid)
		}

		// Debugln formats and queues before the level check; guard it down to one comparison.
		if log.Level() == log.DEBUG {
			log.Debugln("[PKG] %s --> %s by %d[%s]", metadata.SourceAddress(), metadata.RemoteAddress(), uid, pkg)
		}

		return pkg, nil
	}

	// Must stay non-nil even though it does nothing: mihomo reads a non-nil DefaultSocketHook as
	// "CMFA host" and skips interfaceName/routingMark binding and TFO, which this process cannot
	// do. No per-socket protect either: the core's uid is excluded from the tunnel.
	dialer.DefaultSocketHook = func(string, string, syscall.RawConn) error { return nil }
}

// Launcher RPC first on API 29+ (where /proc/net hides other uids' rows), /proc/net on a miss.
func socketOwner(sdk int, query ownerQuery, source, target net.Addr) (int, string) {
	var protocol int
	// RawSrcAddr is always a *net.TCPAddr / *net.UDPAddr; Network() is never family-suffixed.
	switch source.Network() {
	case "tcp":
		protocol = syscall.IPPROTO_TCP
	case "udp":
		protocol = syscall.IPPROTO_UDP
	default:
		return -1, ""
	}

	// Only the RPC needs a usable target; /proc/net matches on the local address alone.
	if query != nil && sdk >= 29 && addrUsable(target) {
		if uid, pkg := query(protocol, source.String(), target.String()); uid >= 0 || pkg != "" {
			return uid, pkg
		}
	}
	return procNetUID(source), ""
}

// Rejects a typed nil boxed in net.Addr and hostname-only addresses: the launcher would reject
// both, so the IPC round trip would be wasted.
func addrUsable(addr net.Addr) bool {
	switch value := addr.(type) {
	case *net.TCPAddr:
		return value != nil && value.IP != nil
	case *net.UDPAddr:
		return value != nil && value.IP != nil
	default:
		return addr != nil
	}
}
