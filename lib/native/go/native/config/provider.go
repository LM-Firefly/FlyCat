// Package config provides configuration loading and processing for the native bridge.
package config

import (
	"github.com/metacubex/mihomo/config"
)

// Provider prefix constants for iterating proxy and rule providers.
const (
	PROXIES = "proxies"
	RULES   = "rules"
)

func forEachProviders(rawCfg *config.RawConfig, fun func(index int, total int, key string, provider map[string]any, prefix string)) {
	total := len(rawCfg.ProxyProvider) + len(rawCfg.RuleProvider)
	index := 0

	for k, v := range rawCfg.ProxyProvider {
		fun(index, total, k, v, PROXIES)

		index++
	}

	for k, v := range rawCfg.RuleProvider {
		fun(index, total, k, v, RULES)

		index++
	}
}
