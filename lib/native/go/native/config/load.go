// Package config provides configuration loading and processing for the native bridge.
package config

import (
	"os"
	P "path"
	"strings"

	"github.com/metacubex/mihomo/component/age"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub"
)

// UnmarshalAndPatch reads and decrypts a profile's config.yaml, then applies native patches.
func UnmarshalAndPatch(profilePath string) (*config.RawConfig, error) {
	configPath := P.Join(profilePath, "config.yaml")

	configData, err := os.ReadFile(configPath)
	if err != nil {
		return nil, err
	}

	// Attempt age decryption if a secret key is configured
	if key := GetAgeSecretKey(); key != "" {
		if decData, decErr := age.DecryptBytes(configData, key); decErr == nil {
			configData = decData
		}
	}
	hasExplicitLogLevel := hasTopLevelYamlKey(configData, "log-level")

	rawConfig, err := config.UnmarshalRawConfig(configData)
	if err != nil {
		return nil, err
	}

	if !hasExplicitLogLevel {
		rawConfig.LogLevel = defaultCoreLogLevel
	}

	if err := process(rawConfig, profilePath); err != nil {
		return nil, err
	}

	return rawConfig, nil
}

func hasTopLevelYamlKey(data []byte, key string) bool {
	target := normalizeYamlKey(key)
	for _, rawLine := range strings.Split(string(data), "\n") {
		line := strings.TrimRight(rawLine, "\r")
		if strings.TrimSpace(line) == "" {
			continue
		}
		trimmedLeft := strings.TrimLeft(line, " \t")
		if strings.HasPrefix(trimmedLeft, "#") {
			continue
		}
		if len(trimmedLeft) != len(line) {
			// nested key
			continue
		}
		idx := strings.IndexRune(line, ':')
		if idx <= 0 {
			continue
		}
		candidate := normalizeYamlKey(line[:idx])
		if candidate == target {
			return true
		}
	}
	return false
}

func normalizeYamlKey(key string) string {
	trimmed := strings.TrimSpace(key)
	trimmed = strings.Trim(trimmed, `"'`)
	return trimmed
}

// LoadDefault applies an empty default configuration.
func LoadDefault() {
	cfg, err := config.Parse([]byte{})
	if err != nil {
		panic(err.Error())
	}

	hub.ApplyConfig(cfg)
}
