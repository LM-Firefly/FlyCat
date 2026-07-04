package tun

import (
	"encoding/json"
	"io"
	"net"
	"net/netip"
	"strings"

	C "github.com/metacubex/mihomo/constant"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/sing_tun"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

func splitGatewayPrefixes(gateway string) ([]netip.Prefix, []netip.Prefix, error) {
	var prefix4 []netip.Prefix
	var prefix6 []netip.Prefix
	for _, gatewayStr := range strings.Split(gateway, ",") { // "172.19.0.1/30" or "172.19.0.1/30,fdfe:dcba:9876::1/126"
		gatewayStr = strings.TrimSpace(gatewayStr)
		if len(gatewayStr) == 0 {
			continue
		}
		prefix, err := netip.ParsePrefix(gatewayStr)
		if err != nil {
			return nil, nil, err
		}

		if prefix.Addr().Is4() {
			prefix4 = append(prefix4, prefix)
		} else {
			prefix6 = append(prefix6, prefix)
		}
	}
	return prefix4, prefix6, nil
}

func splitDNSHijack(dns string) []string {
	var dnsHijack []string
	for _, dnsStr := range strings.Split(dns, ",") { // "172.19.0.2" or "0.0.0.0"
		dnsStr = strings.TrimSpace(dnsStr)
		if len(dnsStr) == 0 {
			continue
		}
		dnsHijack = append(dnsHijack, net.JoinHostPort(dnsStr, "53"))
	}
	return dnsHijack
}

func Start(fd int, stack, gateway, portal, dns string) (io.Closer, error) {
	log.Debugln("TUN: fd = %d, stack = %s, gateway = %s, portal = %s, dns = %s", fd, stack, gateway, portal, dns)

	tunStack, ok := C.StackTypeMapping[strings.ToLower(stack)]
	if !ok {
		tunStack = C.TunSystem
	}

	prefix4, prefix6, err := splitGatewayPrefixes(gateway)
	if err != nil {
		log.Errorln("TUN: %v", err)
		return nil, err
	}

	dnsHijack := splitDNSHijack(dns)

	options := LC.Tun{
		Enable:              true,
		Device:              sing_tun.InterfaceName,
		Stack:               tunStack,
		DNSHijack:           dnsHijack,
		AutoRoute:           false, // had set route in TunService.kt
		AutoDetectInterface: false, // implements by VpnService::protect
		Inet4Address:        prefix4,
		Inet6Address:        prefix6,
		MTU:                 9000, // private const val TUN_MTU = 9000 in TunService.kt
		FileDescriptor:      fd,
	}

	tunOptions, _ := json.Marshal(options)
	log.Debugln("%s", string(tunOptions))

	listener, err := sing_tun.New(options, tunnel.Tunnel)
	if err != nil {
		log.Errorln("TUN: %v", err)
		return nil, err
	}

	return listener, nil
}
