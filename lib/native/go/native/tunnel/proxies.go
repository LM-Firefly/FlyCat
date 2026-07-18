// Package tunnel provides proxy tunnel management for the native bridge.
package tunnel

import (
	"sort"
	"strings"
	"sync/atomic"

	"github.com/dlclark/regexp2"

	"cfa/native/config"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

// proxyGroupVersion is incremented when proxy group structure or selection changes.
var proxyGroupVersion atomic.Uint64

// ProxyGroupVersion returns the current proxy group version.
func ProxyGroupVersion() uint64 {
	return proxyGroupVersion.Load()
}

// IncrProxyGroupVersion increments the proxy group version counter.
func IncrProxyGroupVersion() {
	proxyGroupVersion.Add(1)
}

// SortMode defines the sorting order for proxy lists.
type SortMode int

// Sort mode constants for proxy list ordering.
const (
	Default SortMode = iota
	Title
	Delay
)

// Proxy represents a simplified proxy entry for UI display.
type Proxy struct {
	Name     string `json:"name"`
	Title    string `json:"title"`
	Subtitle string `json:"subtitle"`
	Type     string `json:"type"`
	Delay    int    `json:"delay"`
	IsGroup  bool   `json:"isGroup"`
}

// ProxyGroup represents a proxy group with its member proxies.
type ProxyGroup struct {
	Name    string   `json:"name,omitempty"`
	Type    string   `json:"type"`
	Now     string   `json:"now"`
	Icon    string   `json:"icon,omitempty"`
	Hidden  bool     `json:"hidden"`
	Fixed   string   `json:"fixed"`
	Proxies []*Proxy `json:"proxies"`
}

type sortableProxyList struct {
	list []*Proxy
	less func(a, b *Proxy) bool
}

func (s *sortableProxyList) Len() int {
	return len(s.list)
}

func (s *sortableProxyList) Less(i, j int) bool {
	return s.less(s.list[i], s.list[j])
}

func (s *sortableProxyList) Swap(i, j int) {
	s.list[i], s.list[j] = s.list[j], s.list[i]
}

// QueryProxyGroupNames returns the names of all proxy groups.
func QueryProxyGroupNames(excludeNotSelectable bool) []string {
	mode := tunnel.Mode()

	if mode == tunnel.Direct {
		return []string{}
	}

	global := tunnel.Proxies()["GLOBAL"].Adapter().(outboundgroup.ProxyGroup)
	proxies := global.Providers()[0].Proxies()
	result := make([]string, 0, len(proxies)+1)

	if mode == tunnel.Global {
		result = append(result, "GLOBAL")
	}

	for _, p := range proxies {
		if _, ok := p.Adapter().(outboundgroup.ProxyGroup); ok {
			if !excludeNotSelectable || p.Type() == C.Selector {
				result = append(result, p.Name())
			}
		}
	}

	return result
}

// QueryProxyGroup returns the details of a proxy group including its sorted member proxies.
func QueryProxyGroup(name string, sortMode SortMode, uiSubtitlePattern *regexp2.Regexp) *ProxyGroup {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Query group `%s`: not found", name)

		return nil
	}

	adapter := p.Adapter()
	g, ok := adapter.(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Query group `%s`: invalid type %s", name, p.Type().String())

		return nil
	}

	proxies := convertProxies(g.Proxies(), uiSubtitlePattern)

	switch sortMode {
	case Title:
		wrapper := &sortableProxyList{
			list: proxies,
			less: func(a, b *Proxy) bool {
				return strings.Compare(a.Title, b.Title) < 0
			},
		}

		sort.Sort(wrapper)
	case Delay:
		wrapper := &sortableProxyList{
			list: proxies,
			less: func(a, b *Proxy) bool {
				return a.Delay < b.Delay
			},
		}

		sort.Sort(wrapper)
	case Default:
	default:
	}

	return &ProxyGroup{
		Name:    name,
		Type:    config.NormalizeProxyType(g.Type().String()),
		Now:     g.Now(),
		Icon:    proxyGroupIcon(g),
		Hidden:  g.Hidden(),
		Fixed:   config.ExtractFixedFromAdapter(adapter),
		Proxies: proxies,
	}
}

func proxyGroupIcon(group outboundgroup.ProxyGroup) string {
	return group.Icon()
}

// QueryProxyGroupsBatch resolves multiple proxy groups in a single call, avoiding N separate JSON serialization + CGO round-trips. Groups that cannot be resolved are silently skipped.
func QueryProxyGroupsBatch(names []string, sortMode SortMode, uiSubtitlePattern *regexp2.Regexp) []*ProxyGroup {
	result := make([]*ProxyGroup, 0, len(names))
	for _, name := range names {
		group := QueryProxyGroup(name, sortMode, uiSubtitlePattern)
		if group != nil {
			result = append(result, group)
		}
	}
	return result
}

// PatchSelector sets the selected proxy in a selector group and persists the choice.
func PatchSelector(selector, name string) bool {
	p := tunnel.Proxies()[selector]

	if p == nil {
		log.Warnln("Patch selector `%s`: not found", selector)

		return false
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Patch selector `%s`: invalid type %s", selector, p.Type().String())

		return false
	}

	s, ok := g.(outboundgroup.SelectAble)
	if !ok {
		log.Warnln("Patch selector `%s`: invalid type %s", selector, p.Type().String())

		return false
	}

	if err := s.Set(name); err != nil {
		log.Warnln("Patch selector `%s`: %s", selector, err.Error())
		return false
	}

	cachefile.Cache().SetSelected(selector, name)

	log.Infoln("Patch selector %s -> %s", selector, name)

	closeConnByGroup(selector)

	return true
}

// PatchForceSelector forcefully sets the selected proxy, bypassing validation.
func PatchForceSelector(selector, name string) bool {
	p := tunnel.Proxies()[selector]
	if p == nil {
		log.Warnln("Force patch selector `%s`: not found", selector)
		return false
	}
	adapter := p.Adapter()
	if _, ok := adapter.(outboundgroup.ProxyGroup); !ok {
		log.Warnln("Force patch selector `%s`: invalid type %s", selector, p.Type().String())
		return false
	}
	s, ok := adapter.(interface{ ForceSet(string) })
	if !ok {
		log.Warnln("Force patch selector `%s`: not supported", selector)
		return false
	}
	s.ForceSet(name)
	cachefile.Cache().SetSelected(selector, name)
	log.Infoln("Force patch selector %s -> %s", selector, name)
	closeConnByGroup(selector)
	return true
}

func splitTitleSubtitle(name string, typeName string, isGroup bool, uiSubtitlePattern *regexp2.Regexp) (string, string) {
	title := name
	subtitle := typeName

	if uiSubtitlePattern != nil && !isGroup {
		runes := []rune(name)
		match, err := uiSubtitlePattern.FindRunesMatch(runes)
		if err == nil && match != nil {
			title = string(runes[:match.Index]) + string(runes[match.Index+match.Length:])
			subtitle = string(runes[match.Index : match.Index+match.Length])
		}
	}

	return title, subtitle
}

func convertProxies(proxies []C.Proxy, uiSubtitlePattern *regexp2.Regexp) []*Proxy {
	result := make([]*Proxy, 0, 128)

	for _, p := range proxies {
		name := p.Name()
		_, isGroup := p.Adapter().(outboundgroup.ProxyGroup)
		title, subtitle := splitTitleSubtitle(name, p.Type().String(), isGroup, uiSubtitlePattern)

		testURL := "https://www.gstatic.com/generate_204"
		for k := range p.ExtraDelayHistories() {
			if len(k) > 0 {
				testURL = k
				break
			}
		}

		result = append(result, &Proxy{
			Name:     name,
			Title:    strings.TrimSpace(title),
			Subtitle: strings.TrimSpace(subtitle),
			Type:     config.NormalizeProxyType(p.Type().String()),
			Delay:    int(p.LastDelayForTestUrl(testURL)),
			IsGroup:  isGroup,
		})
	}
	return result
}
