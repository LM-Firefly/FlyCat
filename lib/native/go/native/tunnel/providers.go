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

type Provider struct {
	Name             string            `json:"name"`
	VehicleType      string            `json:"vehicleType"`
	Type             string            `json:"type"`
	UpdatedAt        int64             `json:"updatedAt"`
	Path             string            `json:"path"`
	SubscriptionInfo *SubscriptionInfo `json:"subscriptionInfo,omitempty"`
	Count            int               `json:"count"`
	AgeSecretKey     string            `json:"age-secret-key,omitempty"`
}

type SubscriptionInfo struct {
	Upload   int64 `json:"Upload"`
	Download int64 `json:"Download"`
	Total    int64 `json:"Total"`
	Expire   int64 `json:"Expire"`
}

type UpdatableProvider interface {
	UpdatedAt() time.Time
}

type VehicleProvider interface {
	Vehicle() provider.Vehicle
}

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
		if cached := cachefile.Cache().GetSubscriptionInfo(p.Name()); cached != "" {
			log.Debugln("[QueryProviders] %s: bbolt hit: %s", p.Name(), cached)
			item.SubscriptionInfo = parseSubscriptionInfoString(cached)
		} else {
			log.Debugln("[QueryProviders] %s: bbolt miss, trying json.Marshal", p.Name())
			if raw, err := json.Marshal(p); err == nil {
				var data struct {
					SubscriptionInfo *SubscriptionInfo `json:"subscriptionInfo,omitempty"`
					AgeSecretKey     string            `json:"age-secret-key,omitempty"`
				}
				if err2 := json.Unmarshal(raw, &data); err2 == nil {
					if data.SubscriptionInfo != nil {
						log.Debugln("[QueryProviders] %s: json.Marshal hit subscriptionInfo", p.Name())
					} else {
						log.Debugln("[QueryProviders] %s: json.Marshal subscriptionInfo=nil", p.Name())
					}
					item.SubscriptionInfo = data.SubscriptionInfo
					if data.AgeSecretKey != "" {
						item.AgeSecretKey = data.AgeSecretKey
					}
				} else {
					log.Warnln("[QueryProviders] %s: json.Unmarshal failed: %v", p.Name(), err2)
				}
			} else {
				log.Warnln("[QueryProviders] %s: json.Marshal failed: %v", p.Name(), err)
			}
		}
		result = append(result, item)
	}

	return result
}

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
