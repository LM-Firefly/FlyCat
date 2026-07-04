package tun

import (
	"fmt"
	"io"
	"net/netip"
	"strings"

	C "github.com/metacubex/mihomo/constant"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/sing_tun"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
	"gopkg.in/yaml.v3"
)

type RootTunConfig struct {
	IfName             string   `json:"ifName" yaml:"ifName"`
	MTU                int      `json:"mtu" yaml:"mtu"`
	Stack              string   `json:"stack" yaml:"stack"`
	Inet4Address       []string `json:"inet4Address" yaml:"inet4Address"`
	Inet6Address       []string `json:"inet6Address" yaml:"inet6Address"`
	DNSHijack          []string `json:"dnsHijack" yaml:"dnsHijack"`
	AutoRoute          bool     `json:"autoRoute" yaml:"autoRoute"`
	StrictRoute        bool     `json:"strictRoute" yaml:"strictRoute"`
	AutoRedirect       bool     `json:"autoRedirect" yaml:"autoRedirect"`
	IncludeUID         []uint32 `json:"includeUid" yaml:"includeUid"`
	ExcludeUID         []uint32 `json:"excludeUid" yaml:"excludeUid"`
	IncludeAndroidUser []int    `json:"includeAndroidUser" yaml:"includeAndroidUser"`
	RouteAddress       []string `json:"routeAddress" yaml:"routeAddress"`
	RouteExclude       []string `json:"routeExcludeAddress" yaml:"routeExcludeAddress"`
	DNSMode            string   `json:"dnsMode" yaml:"dnsMode"`
	FakeIPRange        string   `json:"fakeIpRange" yaml:"fakeIpRange"`
	FakeIPRange6       string   `json:"fakeIpRange6" yaml:"fakeIpRange6"`
	AllowIPv6          bool     `json:"allowIpv6" yaml:"allowIpv6"`
}

func StartRoot(configYaml string) (io.Closer, error) {
	var cfg RootTunConfig
	if err := yaml.Unmarshal([]byte(configYaml), &cfg); err != nil {
		return nil, fmt.Errorf("decode root tun config: %w", err)
	}

	log.Debugln(
		"ROOT_TUN: native includeUid=%d excludeUid=%v dnsHijack=%v routeAddress=%d",
		len(cfg.IncludeUID),
		cfg.ExcludeUID,
		cfg.DNSHijack,
		len(cfg.RouteAddress),
	)

	options, err := cfg.toListenerOptions()
	if err != nil {
		return nil, err
	}

	payload, _ := yaml.Marshal(cfg)
	log.Debugln("ROOT_TUN: config=\n%s", strings.TrimSpace(string(payload)))

	listener, err := sing_tun.New(options, tunnel.Tunnel)
	if err != nil {
		log.Errorln("ROOT_TUN: %v", err)
		return nil, err
	}

	return listener, nil
}

type tunAddresses struct {
	inet4        []netip.Prefix
	inet6        []netip.Prefix
	route        []netip.Prefix
	routeExclude []netip.Prefix
}

func (c RootTunConfig) parseAddresses() (tunAddresses, error) {
	inet4Address, err := parsePrefixes(c.Inet4Address)
	if err != nil {
		return tunAddresses{}, fmt.Errorf("parse inet4Address: %w", err)
	}

	inet6Address, err := parsePrefixes(c.Inet6Address)
	if err != nil {
		return tunAddresses{}, fmt.Errorf("parse inet6Address: %w", err)
	}

	if !c.AllowIPv6 {
		inet6Address = nil
	}

	routeAddress, err := parsePrefixes(c.RouteAddress)
	if err != nil {
		return tunAddresses{}, fmt.Errorf("parse routeAddress: %w", err)
	}

	routeExcludeAddress, err := parsePrefixes(c.RouteExclude)
	if err != nil {
		return tunAddresses{}, fmt.Errorf("parse routeExcludeAddress: %w", err)
	}

	return tunAddresses{
		inet4:        inet4Address,
		inet6:        inet6Address,
		route:        routeAddress,
		routeExclude: routeExcludeAddress,
	}, nil
}

func (c RootTunConfig) toListenerOptions() (LC.Tun, error) {
	stack, ok := C.StackTypeMapping[strings.ToLower(c.Stack)]
	if !ok {
		stack = C.TunSystem
	}

	addresses, err := c.parseAddresses()
	if err != nil {
		return LC.Tun{}, err
	}

	if err := validateDNSMode(c.DNSMode); err != nil {
		return LC.Tun{}, err
	}

	if err := validateFakeIP(c, addresses.inet4, addresses.inet6); err != nil {
		return LC.Tun{}, err
	}

	return LC.Tun{
		Enable:              true,
		Device:              c.IfName,
		Stack:               stack,
		DNSHijack:           append([]string(nil), c.DNSHijack...),
		AutoRoute:           c.AutoRoute,
		StrictRoute:         c.StrictRoute,
		AutoRedirect:        c.AutoRedirect,
		AutoDetectInterface: false,
		MTU:                 uint32(c.MTU),
		Inet4Address:        addresses.inet4,
		Inet6Address:        addresses.inet6,
		IncludeUID:          append([]uint32(nil), c.IncludeUID...),
		ExcludeUID:          append([]uint32(nil), c.ExcludeUID...),
		IncludeAndroidUser:  append([]int(nil), c.IncludeAndroidUser...),
		RouteAddress:        addresses.route,
		RouteExcludeAddress: addresses.routeExclude,
		FileDescriptor:      0,
	}, nil
}

func parsePrefixes(values []string) ([]netip.Prefix, error) {
	prefixes := make([]netip.Prefix, 0, len(values))
	for _, value := range values {
		value = strings.TrimSpace(value)
		if value == "" {
			continue
		}

		prefix, err := netip.ParsePrefix(value)
		if err != nil {
			return nil, err
		}

		prefixes = append(prefixes, prefix)
	}

	return prefixes, nil
}

func validateDNSMode(mode string) error {
	switch mode {
	case "", "redir-host", "fake-ip":
		return nil
	default:
		return fmt.Errorf("unsupported dnsMode: %s", mode)
	}
}

func hasFakeIPRange(cfg RootTunConfig) bool {
	return cfg.FakeIPRange != "" || (cfg.AllowIPv6 && cfg.FakeIPRange6 != "")
}

func validateFakeIPRange(field string, value string, enabled bool, tunAddresses []netip.Prefix) error {
	if !enabled || value == "" {
		return nil
	}

	fake, err := netip.ParsePrefix(strings.TrimSpace(value))
	if err != nil {
		return fmt.Errorf("parse %s: %w", field, err)
	}

	for _, prefix := range tunAddresses {
		if prefixOverlaps(fake, prefix) {
			return fmt.Errorf("%s overlaps tun subnet: %s", field, prefix.String())
		}
	}

	return nil
}

func validateFakeIP(cfg RootTunConfig, inet4Address []netip.Prefix, inet6Address []netip.Prefix) error {
	if cfg.DNSMode != "fake-ip" {
		return nil
	}

	if !hasFakeIPRange(cfg) {
		return fmt.Errorf("fake-ip requires at least one fake ip range")
	}

	if err := validateFakeIPRange("fakeIpRange", cfg.FakeIPRange, true, inet4Address); err != nil {
		return err
	}

	return validateFakeIPRange("fakeIpRange6", cfg.FakeIPRange6, cfg.AllowIPv6, inet6Address)
}

func prefixOverlaps(left netip.Prefix, right netip.Prefix) bool {
	return left.Contains(right.Addr()) || right.Contains(left.Addr())
}
