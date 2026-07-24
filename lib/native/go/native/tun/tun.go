package tun

import (
	"net"
	"net/netip"
	"strings"

	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/sing_tun"
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

// Configure injects the VpnService TUN fd into the parsed config before ApplyConfig.
func Configure(cfg *config.Config, fd int, gateway, portal, dns string) error {
	_ = portal // reserved; the portal address is carried by the VpnService side, not the core tun

	prefix4, prefix6, err := splitGatewayPrefixes(gateway)
	if err != nil {
		return err
	}

	cfg.General.Tun = LC.Tun{
		Enable:              true,
		Device:              sing_tun.InterfaceName,
		Stack:               C.TunGvisor,
		DNSHijack:           splitDNSHijack(dns),
		AutoRoute:           false, // routes are set by the VpnService.Builder
		AutoDetectInterface: false, // the core's own uid is excluded from the tunnel, so no protect
		Inet4Address:        prefix4,
		Inet6Address:        prefix6,
		MTU:                 1500,
		FileDescriptor:      fd,
	}

	return nil
}
