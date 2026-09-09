// Package tunnel provides rule matching and routing for the native bridge.
package tunnel

import (
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/tunnel"
)

// Rule is a runtime rule entry for UI.
type Rule struct {
	Index     int    `json:"index"`
	Type      string `json:"type"`
	Payload   string `json:"payload"`
	Proxy     string `json:"proxy"`
	Size      int    `json:"size"`
	Disabled  bool   `json:"disabled"`
	HitCount  uint64 `json:"hitCount"`
	MissCount uint64 `json:"missCount"`
}

// QueryRules returns all runtime rules.
func QueryRules() []Rule {
	rawRules := tunnel.Rules()
	rules := make([]Rule, 0, len(rawRules))
	for index, rule := range rawRules {
		item := Rule{
			Index:   index,
			Type:    rule.RuleType().String(),
			Payload: rule.Payload(),
			Proxy:   rule.Adapter(),
			Size:    -1,
		}

		if wrapper, ok := rule.(C.RuleWrapper); ok {
			item.Disabled = wrapper.IsDisabled()
			item.HitCount = wrapper.HitCount()
			item.MissCount = wrapper.MissCount()
			rule = wrapper.Unwrap()
		}

		if rule.RuleType() == C.GEOIP || rule.RuleType() == C.GEOSITE {
			item.Size = rule.(C.RuleGroup).GetRecodeSize()
		}

		rules = append(rules, item)
	}
	return rules
}

// SetRuleDisabled enables/disables one runtime rule by index.
func SetRuleDisabled(index int, disabled bool) bool {
	rules := tunnel.Rules()
	if index < 0 || index >= len(rules) {
		return false
	}
	wrapper, ok := rules[index].(C.RuleWrapper)
	if !ok {
		return false
	}
	wrapper.SetDisabled(disabled)
	return true
}
