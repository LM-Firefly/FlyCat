package app

import (
	"strings"

	"github.com/metacubex/mihomo/dns"
)

func NotifyDNSChanged(dnsList string) {
	var addr []string
	if len(dnsList) > 0 {
		addr = strings.Split(dnsList, ",")
	}
	dns.UpdateSystemDNS(addr)
	dns.FlushCacheWithDefaultResolver()
}
