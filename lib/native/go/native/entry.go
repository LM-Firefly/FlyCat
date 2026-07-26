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
	"sync"
	"syscall"

	"cfa/native/app"
	"cfa/native/delegate"
	"cfa/native/tun"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/listener/tproxy"
	"github.com/metacubex/mihomo/log"
)

func main() {
	var (
		home        = flag.String("home", "", "core home directory")
		versionName = flag.String("version-name", "", "app version name")
		gitVersion  = flag.String("git-version", "", "git version string")
		sdkVersion  = flag.Int("sdk", 0, "android platform SDK int")
		controller  = flag.String("controller", "", "external-controller-unix socket path")
		gateway     = flag.String("gateway", "", "tun gateway CIDR(s)")
		portal      = flag.String("portal", "", "tun portal address(es)")
		dns         = flag.String("dns", "", "tun DNS hijack address(es)")
		mode        = flag.String("mode", "vpn", "run mode: vpn | tun | tproxy")
		configPath  = flag.String("config", "", "compiled config path; root modes read the config here instead of the channel")
		testConfig  = flag.Bool("test", false, "parse config and exit (mihomo -t equivalent); requires --config")
	)
	flag.Parse()

	// Import-time validation: parse only, no controller/TUN/ApplyConfig side effects.
	// Official mihomo -t always has a home (e.g. ~/.config/mihomo). Without SetHomeDir,
	// GEOIP/GEOSITE rule parsing builds an empty MMDB path and fails with
	// `open : no such file or directory` even though the config itself is fine.
	if *testConfig {
		if *configPath == "" {
			fatal("--test requires --config")
		}
		if *home != "" {
			constant.SetHomeDir(*home)
		}
		data, err := os.ReadFile(*configPath)
		if err != nil {
			fatal("read config %q: %v", *configPath, err)
		}
		if _, err := config.Parse(data); err != nil {
			fatal("configuration file %s test failed: %v", *configPath, err)
		}
		fmt.Fprintln(os.Stdout, "configuration file", *configPath, "test is successful")
		return
	}

	if *home == "" {
		fatal("missing required --home")
	}

	// Keep lifecycle I/O off the startup and shutdown paths.
	log.SetLevel(log.ERROR)

	delegate.Init(*home, *versionName, *gitVersion, *sdkVersion)

	var (
		rawConfig []byte
		tunFd     = -1
		channelFd = -1
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
		channelFd = channelFromEnv()
		if channelFd < 0 {
			fatal("missing CHANNEL: vpn mode delivers config over the socketpair, not a file")
		}
		data, fd, err := readSetup(channelFd)
		if err != nil {
			fatal("read setup from channel: %v", err)
		}
		rawConfig, tunFd = data, fd
	}
	if channelFd >= 0 {
		defer syscall.Close(channelFd)
		app.ApplyTunContext(nil, socketOwnerQuery(channelFd))
	} else {
		app.ApplyTunContext(nil, nil)
	}
	cfg, err := config.Parse(rawConfig)
	if err != nil {
		fatal("parse compiled config: %v", err)
	}
	// Profile log-level must not reopen the floodgates after ApplyConfig.
	cfg.General.LogLevel = log.ERROR
	log.SetLevel(log.ERROR)
	if *controller != "" {
		cfg.Controller.ExternalControllerUnix = *controller
	}

	if *mode == "vpn" && tunFd >= 0 {
		if err := tun.Configure(cfg, tunFd, *gateway, *portal, *dns); err != nil {
			fatal("configure tun: %v", err)
		}
	}

	hub.ApplyConfig(cfg)
	log.SetLevel(log.ERROR)

	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)
	<-signals

	if *mode == "tproxy" {
		tproxy.CleanupTProxyIPTables()
	}
}

func channelFromEnv() int {
	value := os.Getenv("CHANNEL")
	if value == "" {
		return -1
	}
	fd, err := strconv.Atoi(value)
	if err != nil {
		log.Errorln("[core] ignoring malformed CHANNEL=%q: %v", value, err)
		return -1
	}
	return fd
}

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
			return config, fd, nil
		}
		if n == 0 {
			return config, -1, nil
		}
		config = append(config, buf[:n]...)
	}
}

func socketOwnerQuery(channelFd int) func(int, string, string) string {
	var mutex sync.Mutex
	return func(protocol int, source, target string) string {
		mutex.Lock()
		defer mutex.Unlock()

		request := []byte(fmt.Sprintf("%d\t%s\t%s", protocol, source, target))
		if err := syscall.Sendmsg(channelFd, request, nil, nil, 0); err != nil {
			return "-1\t"
		}
		response := make([]byte, 4096)
		n, _, _, _, err := syscall.Recvmsg(channelFd, response, nil, 0)
		if err != nil || n <= 0 {
			return "-1\t"
		}
		return string(response[:n])
	}
}

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
