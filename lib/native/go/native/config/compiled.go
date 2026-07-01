package config

import (
	"runtime"
	"strings"

	"cfa/native/app"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/log"
)

func LoadRaw(configData []byte) error {
	return loadCompiledData(configData)
}

func loadCompiledData(configData []byte) error {
	rawCfg, err := config.UnmarshalRawConfig(configData)
	if err != nil {
		log.Errorln("Load compiled: %s", err.Error())
		return err
	}

	cfg, err := config.Parse(configData)
	if err != nil {
		log.Errorln("Load compiled: %s", err.Error())
		return err
	}

	hub.ApplyConfig(cfg)
	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)
	runtime.GC()
	return nil
}

func QueryProxyGroupsFromCompiledRaw(configRaw string, profileDir string, excludeNotSelectable bool) ([]*ProxyGroup, error) {
	_ = profileDir

	rawCfg, err := UnmarshalCompiledRaw(configRaw)
	if err != nil {
		return nil, err
	}

	cfg, err := config.Parse([]byte(configRaw))
	if err != nil {
		return nil, err
	}

	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)

	groupNames := make([]string, 0, len(rawCfg.ProxyGroup))
	seen := make(map[string]struct{}, len(rawCfg.ProxyGroup))
	for _, mapping := range rawCfg.ProxyGroup {
		name, _ := mapping["name"].(string)
		name = strings.TrimSpace(name)
		if name == "" {
			continue
		}
		if _, ok := seen[name]; ok {
			continue
		}
		seen[name] = struct{}{}
		groupNames = append(groupNames, name)
	}

	return buildProxyGroupsFromParsed(cfg, groupNames, excludeNotSelectable), nil
}

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

func UnmarshalCompiledRaw(configRaw string) (*config.RawConfig, error) {
	return config.UnmarshalRawConfig([]byte(configRaw))
}

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

func QueryGroupNamesFromCompiledRaw(configRaw string, excludeNotSelectable bool) ([]string, error) {
	rawCfg, err := UnmarshalCompiledRaw(configRaw)
	if err != nil {
		return nil, err
	}
	names := make([]string, 0, len(rawCfg.ProxyGroup))
	seen := make(map[string]struct{}, len(rawCfg.ProxyGroup))
	for _, mapping := range rawCfg.ProxyGroup {
		name, _ := mapping["name"].(string)
		name = strings.TrimSpace(name)
		if name == "" {
			continue
		}
		if _, ok := seen[name]; ok {
			continue
		}
		if excludeNotSelectable {
			typeName, _ := mapping["type"].(string)
			if strings.TrimSpace(typeName) != "select" {
				continue
			}
		}
		seen[name] = struct{}{}
		names = append(names, name)
	}
	return names, nil
}
