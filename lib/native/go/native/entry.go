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
	"github.com/metacubex/mihomo/log"
)

// main is the standalone core executable used by the out-of-process architecture. The SAME package
// is also built as the legacy c-shared library (where main is never called), so nothing here may be
// required by the c-shared path.
//
// Config delivery is IN-MEMORY, never on disk: the compiled config (liboverride output — already the
// COMPLETE final mihomo config, since the Go process() chain is validation-only) is streamed from the
// parent over the CHANNEL socketpair, along with the TUN fd. Nothing writes the plaintext config to
// the filesystem. The tun always uses the userspace gVisor stack, whose egress bypasses the tunnel via
// the app's uid exclusion, so app.MarkSocket stays a no-op (no per-socket protect needed).
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

	// The core is silent after fork (stdout/stderr are its only channel out, redirected by the
	// launcher to <home>/core.log). Forward the mihomo log bus there so TUN/DNS bring-up is
	// diagnosable.
	go func() {
		for event := range log.Subscribe() {
			fmt.Fprintf(os.Stderr, "[%s] %s\n", event.Type(), event.Payload)
		}
	}()
	log.SetLevel(log.DEBUG)

	delegate.Init(*home, *versionName, *gitVersion, *sdkVersion)
	// Egress never loops back through the tunnel (VPN excludes the app's own uid; tun uses
	// auto-detect-interface; tproxy uses an iptables bypass), so the core needs no per-socket protect;
	// owner lookups fall back to procfs.
	app.ApplyTunContext(nil, nil)

	// Acquire the compiled config and, for the VpnService path, the TUN fd. The VPN path streams both
	// over the inherited socketpair (never touching disk). The root paths are launched detached via
	// `su` and cannot inherit the channel, so they read the config from --config (a plain file or a
	// fifo) and open their own device — no fd is passed.
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
		// Add the unix socket the app talks to and the bearer secret, but KEEP any user-configured
		// external-controller (TCP) so a web dashboard (metacubexd/yacd) can connect too. The app
		// derives the secret from the config, so the dashboard and the app share one bearer token.
		cfg.Controller.ExternalControllerUnix = *controller
		cfg.Controller.Secret = *secret
	}

	// TUN setup is mode-specific:
	//  - vpn: inject the VpnService fd into the config BEFORE ApplyConfig so mihomo's own updateTun
	//    wraps it with the userspace gVisor stack (egress bypasses the tunnel via the app's uid
	//    exclusion; app.MarkSocket stays the no-op set above).
	//  - tun: the compiled `tun:` block is authoritative — the core opens its OWN kernel device
	//    (auto-route / auto-detect-interface) in the root domain; do NOT overwrite it.
	//  - tproxy: no TUN; the compiled config carries tproxy-port + iptables, applied by ApplyConfig.
	if *mode == "vpn" && tunFd >= 0 {
		if err := tun.Configure(cfg, tunFd, *gateway, *portal, *dns); err != nil {
			fatal("configure tun: %v", err)
		}
	}

	hub.ApplyConfig(cfg)
	log.SetLevel(log.DEBUG) // ApplyConfig may lower the level from the config; keep DEBUG for TUN diagnostics
	log.Infoln("[core] config applied; mode=%s controller=%q tun.enable=%v", *mode, *controller, cfg.General.Tun.Enable)

	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)
	sig := <-signals
	log.Infoln("[core] received %v, shutting down", sig)
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

// readSetup streams the compiled config from the parent over the SEQPACKET channel and returns it
// together with the TUN fd. Config bytes arrive as ordinary data messages; the message that carries
// a descriptor (SCM_RIGHTS) is the terminator and its own data is ignored. A plain EOF terminates a
// no-fd (controller-only) launch. Nothing touches the filesystem — the plaintext config never lands
// on disk.
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
