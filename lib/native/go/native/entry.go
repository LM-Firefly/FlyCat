// This file is part of YumeBox.
//
// YumeBox is free software: you can redistribute it and/or modify it under the
// terms of the GNU Affero General Public License as published by the Free
// Software Foundation, either version 3 of the License.
//
// Copyright (c) YumeYucca 2025 - Present

// Command clash is the mihomo core packaged as a standalone PIE (libclash.so): --test validates
// a config and exits, otherwise it runs until SIGINT/SIGTERM. Launcher side: CoreProcess.kt.
package main

import (
	"flag"
	"fmt"
	"os"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
)

type options struct {
	home       string
	controller string
	gateway    string
	dns        string
	mode       string
	configPath string
	sdk        int
	test       bool
}

func parseOptions() options {
	var o options

	flag.StringVar(&o.home, "home", "", "core home directory")
	flag.StringVar(&o.controller, "controller", "", "external-controller-unix socket path")
	flag.StringVar(&o.gateway, "gateway", "", "tun gateway CIDR(s)")
	flag.StringVar(&o.dns, "dns", "", "tun DNS hijack address(es)")
	flag.StringVar(&o.mode, "mode", "vpn", "run mode: vpn | tun | tproxy")
	flag.StringVar(&o.configPath, "config", "", "compiled config path; root modes read the config here instead of the channel")
	flag.IntVar(&o.sdk, "sdk", 0, "android platform SDK int")
	flag.BoolVar(&o.test, "test", false, "parse config and exit (mihomo -t equivalent); requires --config")
	flag.Parse()

	return o
}

func main() {
	opts := parseOptions()

	// SetHomeDir must precede config.Parse: geo paths resolve through constant.Path.
	log.SetLevel(log.ERROR)
	if opts.home != "" {
		constant.SetHomeDir(opts.home)
	}

	if opts.test {
		testConfig(opts)
		return
	}
	run(opts)
}

// testConfig implements --test: parse only. Wording and exit codes are a contract with
// ProfileConfigTester.kt.
func testConfig(opts options) {
	if opts.configPath == "" {
		fatal("--test requires --config")
	}
	if opts.home == "" {
		// Without a home the geo databases resolve to a cwd path where nothing exists, and a
		// valid config is reported as failing.
		fatal("--test requires --home")
	}

	data, err := os.ReadFile(opts.configPath)
	if err != nil {
		fatal("read config %q: %v", opts.configPath, err)
	}
	if _, err := config.Parse(data); err != nil {
		fatal("configuration file %s test failed: %v", opts.configPath, err)
	}

	fmt.Fprintln(os.Stdout, "configuration file", opts.configPath, "test is successful")
}

func fatal(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "mihomo: "+format+"\n", args...)
	os.Exit(1)
}
