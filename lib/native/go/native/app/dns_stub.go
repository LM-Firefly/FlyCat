//go:build !android || !cmfa

package app

func NotifyDnsChanged(dnsList string) {
	_ = dnsList
}
