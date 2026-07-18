package tunnel

import (
	"github.com/metacubex/mihomo/tunnel/statistic"
)

// ResetStatistic resets all traffic counters to zero.
func ResetStatistic() {
	statistic.DefaultManager.ResetStatistic()
}

// Now returns the current upload and download speeds in bytes per second.
func Now() (up int64, down int64) {
	return statistic.DefaultManager.Now()
}

// Total returns the cumulative upload and download traffic in bytes.
func Total() (up int64, down int64) {
	return statistic.DefaultManager.Total()
}
