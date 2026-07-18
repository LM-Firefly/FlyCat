package config

import (
	"encoding/json"
	"strings"

	"github.com/dlclark/regexp2"

	"cfa/native/app"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	mihomoConfig "github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
)

// ExtractFixedFromAdapter extracts the "fixed" field from a proxy group adapter
// by marshaling it to JSON and parsing the result.
func ExtractFixedFromAdapter(adapter any) string {
	if marshaler, ok := adapter.(json.Marshaler); ok {
		if data, err := marshaler.MarshalJSON(); err == nil {
			var mapData map[string]any
			if err := json.Unmarshal(data, &mapData); err == nil {
				if value, ok := mapData["fixed"].(string); ok {
					return value
				}
			}
		}
	}
	return ""
}

// Proxy 代理结构体（本地定义，避免依赖 tunnel 包）
type Proxy struct {
	Name     string `json:"name"`
	Title    string `json:"title"`
	Subtitle string `json:"subtitle"`
	Type     string `json:"type"`
	Delay    int    `json:"delay"`
	IsGroup  bool   `json:"isGroup"`
}

// ProxyGroup 代理组结构体（本地定义，避免依赖 tunnel 包）
type ProxyGroup struct {
	Name    string   `json:"name"`
	Type    string   `json:"type"`
	Now     string   `json:"now"`
	Icon    string   `json:"icon"`
	Hidden  bool     `json:"hidden"`
	Fixed   string   `json:"fixed"`
	Proxies []*Proxy `json:"proxies"`
}

func buildProxyGroupsFromParsed(
	cfg *mihomoConfig.Config,
	orderedNames []string,
	excludeNotSelectable bool,
) []*ProxyGroup {
	if cfg == nil || len(orderedNames) == 0 {
		return []*ProxyGroup{}
	}

	result := make([]*ProxyGroup, 0, len(orderedNames))
	pattern := app.SubtitlePattern()

	for _, name := range orderedNames {
		proxy := cfg.Proxies[name]
		if proxy == nil {
			continue
		}
		if excludeNotSelectable && proxy.Type() != C.Selector {
			continue
		}

		group, ok := proxy.Adapter().(outboundgroup.ProxyGroup)
		if !ok {
			continue
		}

		result = append(result, &ProxyGroup{
			Name:    name,
			Type:    normalizeProxyType(proxy.Type().String()),
			Now:     group.Now(),
			Icon:    group.Icon(),
			Hidden:  group.Hidden(),
			Fixed:   ExtractFixedFromAdapter(proxy.Adapter()),
			Proxies: convertPreviewProxies(group.Proxies(), pattern),
		})
	}

	return result
}

func convertPreviewProxies(
	proxies []C.Proxy,
	uiSubtitlePattern *regexp2.Regexp,
) []*Proxy {
	result := make([]*Proxy, 0, len(proxies))
	for _, proxy := range proxies {
		result = append(result, buildPreviewProxy(proxy, uiSubtitlePattern))
	}
	return result
}

func buildPreviewProxy(
	proxy C.Proxy,
	uiSubtitlePattern *regexp2.Regexp,
) *Proxy {
	name := proxy.Name()
	title := name
	subtitle := proxy.Type().String()
	_, isGroup := proxy.Adapter().(outboundgroup.ProxyGroup)

	if uiSubtitlePattern != nil {
		if !isGroup {
			runes := []rune(name)
			match, err := uiSubtitlePattern.FindRunesMatch(runes)
			if err == nil && match != nil {
				title = string(runes[:match.Index]) + string(runes[match.Index+match.Length:])
				subtitle = string(runes[match.Index : match.Index+match.Length])
			}
		}
	}

	return &Proxy{
		Name:     name,
		Title:    strings.TrimSpace(title),
		Subtitle: strings.TrimSpace(subtitle),
		Type:     normalizeProxyType(proxy.Type().String()),
		Delay:    int(proxy.LastDelayForTestUrl(getPreviewTestURL(proxy))),
		IsGroup:  isGroup,
	}
}

func getPreviewTestURL(proxy C.Proxy) string {
	for key := range proxy.ExtraDelayHistories() {
		if len(key) > 0 {
			return key
		}
	}
	return "https://www.gstatic.com/generate_204"
}

// normalizeProxyType passes the mihomo adapter type through verbatim. mihomo's
// proxy.Type().String() is the source of truth, and the Kotlin side
// (core/model/Proxy.kt) stores `type` as an opaque string — it never whitelists
// protocols, only categorizing via GROUP_TYPES / MANUALLY_SELECTABLE. Mirroring that
// here keeps the native preview future-proof: a newly added mihomo protocol shows its
// real type instead of being collapsed to "Unknown". Only an empty type falls back.
func normalizeProxyType(proxyType string) string {
	if trimmed := strings.TrimSpace(proxyType); trimmed != "" {
		return trimmed
	}
	return "Unknown"
}
