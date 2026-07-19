// This file is part of YumeBox.
//
// YumeBox is free software: you can redistribute it and/or modify it under the
// terms of the GNU Affero General Public License as published by the Free
// Software Foundation, either version 3 of the License.
//
// Copyright (c) YumeYucca 2025 - Present

package main

import (
	"flag"
	"fmt"
	"os"
	"os/signal"
	"strconv"
	"syscall"

	"cfa/native/app"
	"cfa/native/delegate"
	"cfa/native/tun"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/listener/tproxy"
	"github.com/metacubex/mihomo/log"
)

// main is the standalone core executable (out-of-process architecture). VPN streams the compiled
// config + TUN fd in-memory over the CHANNEL socketpair (never on disk); root modes read --config.
// The same package also builds as the legacy c-shared lib, where main is never called.
func main() {
	var (
		home        = flag.String("home", "", "core home directory")
		versionName = flag.String("version-name", "", "app version name")
		gitVersion  = flag.String("git-version", "", "git version string")
		sdkVersion  = flag.Int("sdk", 0, "android platform SDK int")
		controller  = flag.String("controller", "", "external-controller-unix socket path")
		secret      = flag.String("secret", "", "external controller bearer secret")
		gateway     = flag.String("gateway", "", "tun gateway CIDR(s)")
		portal      = flag.String("portal", "", "tun portal address(es)")
		dns         = flag.String("dns", "", "tun DNS hijack address(es)")
		mode        = flag.String("mode", "vpn", "run mode: vpn | tun | tproxy")
		configPath  = flag.String("config", "", "compiled config path; root modes read the config here instead of the channel")
	)
	flag.Parse()

	if *home == "" {
		fatal("missing required --home")
	}

	// mihomo's logrus already prints to stdout (launcher redirects to <home>/core.log) and ApplyConfig
	// applies the config's log-level; no extra subscriber/forced level, else every line duplicates.

	delegate.Init(*home, *versionName, *gitVersion, *sdkVersion)
	// Egress never loops back (VPN uid-exclude / tun auto-detect-interface / tproxy iptables bypass),
	// so no per-socket protect; owner lookups fall back to procfs.
	app.ApplyTunContext(nil, nil)

	// Acquire config (+ TUN fd for VPN). VPN streams both over the inherited socketpair; detached-su
	// root modes can't inherit it, so they read --config and open their own device (no fd).
	var (
		rawConfig []byte
		tunFd     = -1
	)
	switch *mode {
	case "tun", "tproxy":
		if *configPath == "" {
			fatal("mode %q requires --config", *mode)
		}
		data, err := os.ReadFile(*configPath)
		if err != nil {
			fatal("read config %q: %v", *configPath, err)
		}
		rawConfig = data
	default: // vpn
		channelFd := channelFromEnv()
		if channelFd < 0 {
			fatal("missing CHANNEL: vpn mode delivers config over the socketpair, not a file")
		}
		data, fd, err := readSetup(channelFd)
		if err != nil {
			fatal("read setup from channel: %v", err)
		}
		rawConfig, tunFd = data, fd
	}
	log.Infoln("[core] setup received: mode=%s config=%d bytes tunFd=%d", *mode, len(rawConfig), tunFd)

	// The config is already the COMPLETE final mihomo config — the Rust patch/override layer is the
	// single source of config truth (fake-ip DNS, store-selected, interface-clear, tun block, …).
	// The core only parses and applies it; there is no Go-side config processing.
	cfg, err := config.Parse(rawConfig)
	if err != nil {
		fatal("parse compiled config: %v", err)
	}
	if *controller != "" {
		// Add the app's unix socket + secret but KEEP any user external-controller (TCP) so a web
		// dashboard can connect; the app derives the same secret from the config.
		cfg.Controller.ExternalControllerUnix = *controller
		cfg.Controller.Secret = *secret
	}

	// TUN setup is mode-specific: vpn injects the VpnService fd before ApplyConfig (gVisor stack);
	// tun keeps the compiled `tun:` block authoritative (core opens its own kernel device); tproxy has
	// no TUN (tproxy-port + iptables in the config).
	if *mode == "vpn" && tunFd >= 0 {
		if err := tun.Configure(cfg, tunFd, *gateway, *portal, *dns); err != nil {
			fatal("configure tun: %v", err)
		}
	}

	hub.ApplyConfig(cfg)
	log.Infoln("[core] config applied; mode=%s controller=%q tun.enable=%v", *mode, *controller, cfg.General.Tun.Enable)

	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)
	sig := <-signals
	log.Infoln("[core] received %v, shutting down", sig)

	// TPROXY's iptables/route rules outlive the process; tear them down on exit so a stopped daemon
	// doesn't leave the device's networking hijacked.
	if *mode == "tproxy" {
		tproxy.CleanupTProxyIPTables()
	}
}

// channelFromEnv reads the inherited socketpair fd from CHANNEL (set by libcompat.nativeStart).
func channelFromEnv() int {
	value := os.Getenv("CHANNEL")
	if value == "" {
		return -1
	}
	fd, err := strconv.Atoi(value)
	if err != nil {
		log.Warnln("[core] ignoring malformed CHANNEL=%q: %v", value, err)
		return -1
	}
	return fd
}

// readSetup streams the compiled config from the parent over the SEQPACKET channel with the TUN fd.
// Config arrives as data messages; the SCM_RIGHTS message terminates config (its data ignored), or a
// plain EOF terminates a no-fd launch. Nothing touches the filesystem.
func readSetup(channelFd int) ([]byte, int, error) {
	config := make([]byte, 0, 64*1024)
	buf := make([]byte, 64*1024)
	oob := make([]byte, syscall.CmsgSpace(4))
	for {
		n, oobn, _, _, err := syscall.Recvmsg(channelFd, buf, oob, 0)
		if err != nil {
			return nil, -1, err
		}
		if fd := parseRightsFd(oob[:oobn]); fd >= 0 {
			return config, fd, nil // fd message terminates config; its data is ignored
		}
		if n == 0 {
			return config, -1, nil // EOF without an fd
		}
		config = append(config, buf[:n]...)
	}
}

// parseRightsFd extracts a single SCM_RIGHTS fd from control data, or -1 if none.
func parseRightsFd(oob []byte) int {
	if len(oob) == 0 {
		return -1
	}
	messages, err := syscall.ParseSocketControlMessage(oob)
	if err != nil {
		return -1
	}
	for i := range messages {
		if fds, err := syscall.ParseUnixRights(&messages[i]); err == nil && len(fds) > 0 {
			return fds[0]
		}
	}
	return -1
}

func fatal(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "mihomo: "+format+"\n", args...)
	os.Exit(1)
}
