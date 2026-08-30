// Package config provides configuration loading and processing for the native bridge.
package config

import (
	"strings"

	"cfa/native/app"

	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
)

// QueryProxyGroupsFromCompiledRaw extracts proxy group details from a compiled raw config string.
func QueryProxyGroupsFromCompiledRaw(configRaw string, profileDir string, excludeNotSelectable bool) ([]*ProxyGroup, error) {
	_ = profileDir
	rawCfg, err := UnmarshalCompiledRaw(configRaw)
	if err != nil {
		return nil, err
	}
	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)
	orderedNames := extractOrderedGroupNames(rawCfg.ProxyGroup)
	// Parse the already-unmarshaled rawCfg directly (avoids double unmarshal).
	cfg, err := config.ParseRawConfig(rawCfg)
	if err != nil {
		return nil, err
	}
	return buildProxyGroupsFromParsed(cfg, orderedNames, excludeNotSelectable), nil
}

// QueryProxyGroupNamesFromCompiledRaw extracts proxy group names from a compiled raw config string.
func QueryProxyGroupNamesFromCompiledRaw(configRaw string, excludeNotSelectable bool) ([]string, error) {
	rawCfg, err := UnmarshalCompiledRaw(configRaw)
	if err != nil {
		return nil, err
	}
	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)
	orderedNames := extractOrderedGroupNames(rawCfg.ProxyGroup)
	if !excludeNotSelectable {
		return orderedNames, nil
	}
	cfg, err := config.ParseRawConfig(rawCfg)
	if err != nil {
		return nil, err
	}
	result := make([]string, 0, len(orderedNames))
	for _, name := range orderedNames {
		proxy := cfg.Proxies[name]
		if proxy == nil {
			continue
		}
		if proxy.Type() != C.Selector {
			continue
		}
		result = append(result, name)
	}
	return result, nil
}

func extractOrderedGroupNames(proxyGroup []map[string]any) []string {
	names := make([]string, 0, len(proxyGroup))
	seen := make(map[string]struct{}, len(proxyGroup))
	for _, mapping := range proxyGroup {
		name, _ := mapping["name"].(string)
		name = strings.TrimSpace(name)
		if name == "" {
			continue
		}
		if _, ok := seen[name]; ok {
			continue
		}
		seen[name] = struct{}{}
		names = append(names, name)
	}
	return names
}

// QueryTunRouteExcludeAddressFromCompiledRaw extracts TUN route-exclude addresses from a compiled raw config.
func QueryTunRouteExcludeAddressFromCompiledRaw(configRaw string) ([]string, error) {
	rawCfg, err := UnmarshalCompiledRaw(configRaw)
	if err != nil {
		return nil, err
	}

	addresses := make([]string, 0, len(rawCfg.Tun.RouteExcludeAddress))
	for _, prefix := range rawCfg.Tun.RouteExcludeAddress {
		addresses = append(addresses, prefix.String())
	}
	return addresses, nil
}

// QueryTunPackagesFromCompiledRaw extracts included and excluded package lists from a compiled raw config.
func QueryTunPackagesFromCompiledRaw(configRaw string) ([]string, []string, error) {
	rawCfg, err := UnmarshalCompiledRaw(configRaw)
	if err != nil {
		return nil, nil, err
	}

	return rawCfg.Tun.IncludePackage, rawCfg.Tun.ExcludePackage, nil
}

// UnmarshalCompiledRaw parses a compiled raw config JSON string into a RawConfig.
func UnmarshalCompiledRaw(configRaw string) (*config.RawConfig, error) {
	return config.UnmarshalRawConfig([]byte(configRaw))
}

// ParseCompiledRaw parses a compiled raw config JSON string into both a RawConfig and a fully resolved Config.
func ParseCompiledRaw(configRaw string) (*config.RawConfig, *config.Config, error) {
	rawCfg, err := UnmarshalCompiledRaw(configRaw)
	if err != nil {
		return nil, nil, err
	}
	cfg, err := config.ParseRawConfig(rawCfg)
	if err != nil {
		return nil, nil, err
	}
	return rawCfg, cfg, nil
}
