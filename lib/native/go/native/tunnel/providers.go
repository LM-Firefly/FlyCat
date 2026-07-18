package tunnel

import (
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/metacubex/mihomo/component/profile/cachefile"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

// ErrInvalidType is returned when an unsupported provider type is specified.
var ErrInvalidType = errors.New("invalid type")

func parseSubscriptionInfoString(userinfo string) *SubscriptionInfo {
	if userinfo == "" {
		return nil
	}
	userinfo = strings.ToLower(strings.ReplaceAll(userinfo, " ", ""))
	si := &SubscriptionInfo{}
	hasAny := false
	for _, field := range strings.Split(userinfo, ";") {
		name, value, ok := strings.Cut(field, "=")
		if !ok {
			continue
		}
		v, err := strconv.ParseInt(value, 10, 64)
		if err != nil {
			// Value too large for int64 (e.g. 2^63); try uint64 then clamp
			if uv, err2 := strconv.ParseUint(value, 10, 64); err2 == nil {
				if uv > 1<<63-1 {
					v = 1<<63 - 1 // clamp to MaxInt64
				} else {
					v = int64(uv)
				}
			} else if fv, err3 := strconv.ParseFloat(value, 64); err3 == nil {
				v = int64(fv)
			} else {
				continue
			}
		}
		switch name {
		case "upload":
			si.Upload = v
			hasAny = true
		case "download":
			si.Download = v
			hasAny = true
		case "total":
			si.Total = v
			hasAny = true
		case "expire":
			si.Expire = v
			hasAny = true
		}
	}
	if !hasAny {
		return nil
	}
	return si
}

// Provider represents a proxy or rule provider entry for UI display.
type Provider struct {
	Name             string            `json:"name"`
	VehicleType      string            `json:"vehicleType"`
	Type             string            `json:"type"`
	UpdatedAt        int64             `json:"updatedAt"`
	Path             string            `json:"path"`
	SubscriptionInfo *SubscriptionInfo `json:"subscriptionInfo,omitempty"`
	Count            int               `json:"count"`
	Format           string            `json:"format,omitempty"`
	AgeSecretKey     string            `json:"age-secret-key,omitempty"`
}

// SubscriptionInfo contains traffic quota data parsed from provider headers.
type SubscriptionInfo struct {
	Upload   int64 `json:"Upload"`
	Download int64 `json:"Download"`
	Total    int64 `json:"Total"`
	Expire   int64 `json:"Expire"`
}

// UpdatableProvider is implemented by providers that support refresh.
type UpdatableProvider interface {
	UpdatedAt() time.Time
}

// VehicleProvider is implemented by providers that expose a file vehicle.
type VehicleProvider interface {
	Vehicle() provider.Vehicle
}

// QueryProviders returns metadata for all non-compatible rule and proxy providers.
func QueryProviders() []*Provider {
	r := tunnel.RuleProviders()
	p := tunnel.Providers()

	providers := make([]provider.Provider, 0, len(r)+len(p))

	for _, rule := range r {
		if rule.VehicleType() == provider.Compatible {
			continue
		}

		providers = append(providers, rule)
	}

	for _, proxy := range p {
		if proxy.VehicleType() == provider.Compatible {
			continue
		}

		providers = append(providers, proxy)
	}

	result := make([]*Provider, 0, len(providers))

	for _, p := range providers {
		updatedAt := time.Time{}
		path := ""

		if s, ok := p.(UpdatableProvider); ok {
			updatedAt = s.UpdatedAt()
		}

		if v, ok := p.(VehicleProvider); ok {
			path = v.Vehicle().Path()
		}

		item := &Provider{
			Name:        p.Name(),
			VehicleType: p.VehicleType().String(),
			Type:        p.Type().String(),
			UpdatedAt:   updatedAt.UnixNano() / 1000 / 1000,
			Path:        path,
		}
		if pp, ok := p.(provider.ProxyProvider); ok {
			item.Count = len(pp.Proxies())
		} else if rp, ok := p.(provider.RuleProvider); ok {
			item.Count = rp.Count()
		}
		// Extract format + subscriptionInfo + age-secret-key from provider JSON.
		// Single marshal avoids serializing the full provider (potentially 100KB+ with all proxy nodes) twice when bbolt cache misses.
		if raw, err := json.Marshal(p); err == nil {
			var extra struct {
				Format           string            `json:"format"`
				SubscriptionInfo *SubscriptionInfo `json:"subscriptionInfo,omitempty"`
				AgeSecretKey     string            `json:"age-secret-key,omitempty"`
			}
			if err2 := json.Unmarshal(raw, &extra); err2 == nil {
				if extra.Format != "" {
					item.Format = extra.Format
				}
				if extra.AgeSecretKey != "" {
					item.AgeSecretKey = extra.AgeSecretKey
				}
				// Prefer bbolt-cached subscription info over JSON when available.
				if cached := cachefile.Cache().GetSubscriptionInfo(p.Name()); cached != "" {
					log.Debugln("[QueryProviders] %s: bbolt hit: %s", p.Name(), cached)
					item.SubscriptionInfo = parseSubscriptionInfoString(cached)
				} else {
					item.SubscriptionInfo = extra.SubscriptionInfo
				}
			}
		}
		result = append(result, item)
	}

	return result
}

// UpdateProvider triggers a refresh for the named rule or proxy provider.
func UpdateProvider(t string, name string) error {
	err := ErrInvalidType

	switch t {
	case "Rule":
		p := tunnel.RuleProviders()[name]
		if p == nil {
			return fmt.Errorf("%s not found", name)
		}

		err = p.Update()
	case "Proxy":
		p := tunnel.Providers()[name]
		if p == nil {
			return fmt.Errorf("%s not found", name)
		}

		log.Debugln("[UpdateProvider] updating proxy provider: %s", name)
		err = p.Update()
		if err == nil {
			cached := cachefile.Cache().GetSubscriptionInfo(name)
			log.Debugln("[UpdateProvider] %s update done, bbolt subscriptionInfo=%q", name, cached)
		}
	}

	if err != nil {
		log.Warnln("Updating provider %s: %s", name, err.Error())
	}

	return err
}
