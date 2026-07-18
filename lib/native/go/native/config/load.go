package config

import (
	"os"
	P "path"

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

	rawConfig, err := config.UnmarshalRawConfig(configData)
	if err != nil {
		return nil, err
	}

	if err := process(rawConfig, profilePath); err != nil {
		return nil, err
	}

	return rawConfig, nil
}

// LoadDefault applies an empty default configuration.
func LoadDefault() {
	cfg, err := config.Parse([]byte{})
	if err != nil {
		panic(err.Error())
	}

	hub.ApplyConfig(cfg)
}
