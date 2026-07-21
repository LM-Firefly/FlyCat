package server

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
)

// ─────────────────────────────────────────────────────────────────────────────
// Length-prefixed JSON codec
//
// Wire format:
//   ┌──────────┬──────────────────┐
//   │ 4 bytes  │ N bytes          │
//   │ (BE u32) │ (JSON payload)   │
//   └──────────┴──────────────────┘
// ─────────────────────────────────────────────────────────────────────────────

const (
	maxMessageSize = 16 << 20 // 16 MiB
)

// ReadMessage reads a single length-prefixed JSON message from r into dst.
func ReadMessage(r io.Reader, dst any) error {
	var length uint32
	if err := binary.Read(r, binary.BigEndian, &length); err != nil {
		return fmt.Errorf("read length: %w", err)
	}
	if length > maxMessageSize {
		return fmt.Errorf("message too large: %d bytes (max %d)", length, maxMessageSize)
	}

	buf := make([]byte, length)
	if _, err := io.ReadFull(r, buf); err != nil {
		return fmt.Errorf("read payload: %w", err)
	}

	if err := json.Unmarshal(buf, dst); err != nil {
		return fmt.Errorf("decode json: %w", err)
	}
	return nil
}

// WriteMessage serialises src as JSON and writes it as a length-prefixed frame to w.
func WriteMessage(w io.Writer, src any) error {
	payload, err := json.Marshal(src)
	if err != nil {
		return fmt.Errorf("encode json: %w", err)
	}

	length := uint32(len(payload))
	if err := binary.Write(w, binary.BigEndian, length); err != nil {
		return fmt.Errorf("write length: %w", err)
	}
	if _, err := w.Write(payload); err != nil {
		return fmt.Errorf("write payload: %w", err)
	}
	return nil
}
