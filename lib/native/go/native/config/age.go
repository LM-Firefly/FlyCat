// Package config handles profile loading, decryption, and configuration processing.
package config

import (
	"strings"

	"github.com/metacubex/mihomo/component/age"
)

// SetGlobalSecretKeys sets the global age decryption secret keys, trimming whitespace and skipping empty entries.
func SetGlobalSecretKeys(secretKeys ...string) {
	trimmed := make([]string, 0, len(secretKeys))
	for _, secretKey := range secretKeys {
		key := strings.TrimSpace(secretKey)
		if key == "" {
			continue
		}
		trimmed = append(trimmed, key)
	}
	age.SetGlobalSecretKeys(trimmed...)
}

// GenX25519KeyPair generates a new x25519 age keypair.
func GenX25519KeyPair() (secretKey string, publicKey string, err error) {
	return age.GenX25519KeyPair()
}

// GenHybridKeyPair generates a post-quantum mlkem768-x25519 hybrid age keypair.
// Both x25519 and hybrid keys are fully supported end to end: the Rust override
// decryptor implements the mlkem768x25519 (X-Wing) HPKE recipient.
func GenHybridKeyPair() (secretKey string, publicKey string, err error) {
	return age.GenHybridKeyPair()
}

// ToPublicKeys derives the public keys from the given secret keys.
func ToPublicKeys(secretKeys ...string) (publicKeys []string, err error) {
	trimmed := make([]string, 0, len(secretKeys))
	for _, secretKey := range secretKeys {
		trimmed = append(trimmed, strings.TrimSpace(secretKey))
	}
	return age.ToPublicKeys(trimmed...)
}

// VerifySecretKeys validates that each secret key is well-formed.
func VerifySecretKeys(secretKeys ...string) error {
	trimmed := make([]string, 0, len(secretKeys))
	for _, secretKey := range secretKeys {
		trimmed = append(trimmed, strings.TrimSpace(secretKey))
	}
	return age.VeritySecretKeys(trimmed...)
}

// VerifyPublicKeys validates that each public key is well-formed.
func VerifyPublicKeys(publicKeys ...string) error {
	trimmed := make([]string, 0, len(publicKeys))
	for _, publicKey := range publicKeys {
		trimmed = append(trimmed, strings.TrimSpace(publicKey))
	}
	return age.VerityPublicKeys(trimmed...)
}
