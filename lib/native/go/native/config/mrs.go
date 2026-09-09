// Package config provides configuration loading and processing for the native bridge.
package config

import (
	"bytes"
	"fmt"
	"io"
	"os"

	P "github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/rules/provider"

	"github.com/klauspost/compress/zstd"
)

// ConvertMrsToText reads an MRS binary rule-provider file and returns its content
// as human-readable text (one rule per line). Only Domain and IPCIDR behaviors
// are supported; Classical MRS files will return an error.
func ConvertMrsToText(path string) (string, error) {
	buf, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("read file: %w", err)
	}
	if len(buf) == 0 {
		return "", fmt.Errorf("empty file")
	}

	// Decompress zstd to peek at the behavior byte in the MRS header.
	reader, err := zstd.NewReader(bytes.NewReader(buf))
	if err != nil {
		return "", fmt.Errorf("zstd init: %w", err)
	}
	defer reader.Close()

	// MRS header: Magic(4) + Behavior(1)
	header := make([]byte, 5)
	_, err = io.ReadFull(reader, header)
	if err != nil {
		return "", fmt.Errorf("read mrs header: %w", err)
	}
	// header[4] is the behavior byte: 0=Domain, 1=IPCIDR, 2=Classical
	behaviorByte := header[4]

	var behavior P.RuleBehavior
	switch behaviorByte {
	case 0:
		behavior = P.Domain
	case 1:
		behavior = P.IPCIDR
	case 2:
		return "", fmt.Errorf("classical MRS format is not supported for preview")
	default:
		return "", fmt.Errorf("unknown MRS behavior: %d", behaviorByte)
	}

	// ConvertToMrs with MrsRule input format decodes MRS→text via DumpMrs.
	var out bytes.Buffer
	err = provider.ConvertToMrs(buf, behavior, P.MrsRule, &out)
	if err != nil {
		return "", fmt.Errorf("convert mrs: %w", err)
	}
	return out.String(), nil
}
