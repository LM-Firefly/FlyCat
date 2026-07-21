package main

import (
	"flag"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"cfa/native/app"
	"cfa/native/tunnel"

	"cfa/cmd/clashd/server"
)

func main() {
	socketPath := flag.String("socket", "", "Unix domain socket path (required)")
	home := flag.String("home", "", "mihomo home directory")
	versionName := flag.String("version", "unknown", "App version name")
	gitVersion := flag.String("git-version", "", "Git version (branch_hash_time)")
	sdkVersion := flag.Int("sdk", 0, "Android SDK version")
	trafficInterval := flag.Duration("traffic-interval", 1*time.Second, "Traffic event push interval")
	flag.Parse()

	if *socketPath == "" {
		log.Fatal("[clashd] --socket is required")
	}

	// Create and configure the UDS server.
	srv := server.New(*socketPath)

	// Register all method handlers.
	server.RegisterCoreMethods(srv)
	server.RegisterTunnelMethods(srv)
	server.RegisterConfigMethods(srv)
	server.RegisterSystemMethods(srv)
	server.RegisterLogMethods(srv)

	// Start event pushers.
	server.StartLogEventPusher(srv)
	server.StartTunnelStateEventPusher(srv, 500*time.Millisecond)
	startTrafficPusher(srv, *trafficInterval)

	// Wire up the content context (needed for config fetching).
	app.ApplyContentContext(func(url string) (int, error) {
		// In UDS mode, content opening is handled by the Kotlin side.
		// Return an error indicating the Kotlin side should handle this.
		return -1, nil
	})

	// If home/version are provided, auto-initialise.
	if *home != "" {
		log.Printf("[clashd] auto-init: home=%s version=%s sdk=%d", *home, *versionName, *sdkVersion)
		// delegate.Init will be called when the Kotlin client sends core.init
	}

	// Graceful shutdown on SIGINT/SIGTERM.
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		sig := <-sigCh
		log.Printf("[clashd] received signal %v, shutting down...", sig)
		srv.Stop()
		os.Exit(0)
	}()

	// Start listening (blocks).
	if err := srv.Start(); err != nil {
		log.Fatalf("[clashd] server error: %v", err)
	}
}

// startTrafficPusher periodically pushes traffic stats as events.
func startTrafficPusher(srv *server.Server, interval time.Duration) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		for range ticker.C {
			upNow, downNow := tunnel.Now()
			upTotal, downTotal := tunnel.Total()

			type trafficEvent struct {
				UploadNow     uint64 `json:"uploadNow"`
				DownloadNow   uint64 `json:"downloadNow"`
				UploadTotal   uint64 `json:"uploadTotal"`
				DownloadTotal uint64 `json:"downloadTotal"`
			}

			evt, err := server.NewEvent("traffic", trafficEvent{
				UploadNow:     upNow,
				DownloadNow:   downNow,
				UploadTotal:   upTotal,
				DownloadTotal: downTotal,
			})
			if err != nil {
				continue
			}
			srv.PublishEvent(evt)
		}
	}()
}
