package server

import (
	"encoding/binary"
	"fmt"
	"net"
	"os"

	"golang.org/x/sys/unix"
)

// ─────────────────────────────────────────────────────────────────────────────
// SCM_RIGHTS fd receiving over Unix Domain Socket
//
// This allows the Kotlin side to pass a TUN file descriptor to the Go process
// via the UDS ancillary data mechanism (SCM_RIGHTS).
// ─────────────────────────────────────────────────────────────────────────────

// RecvFD receives a single file descriptor from the given UDS connection
// using SCM_RIGHTS ancillary data. Returns the received fd (>= 0) or an error.
//
// Protocol:
//  1. Kotlin sends a normal message: {method: "tun.passFd", params: {hint: "..."}}
//  2. Kotlin then sends ancillary data with the fd via SCM_RIGHTS
//  3. Go receives the ancillary data and extracts the fd
//
// This function should be called after the "tun.passFd" request has been read,
// while the next bytes on the socket carry the ancillary data.
func RecvFD(conn *net.UnixConn) (int, error) {
	// Read a single byte with ancillary data.
	buf := make([]byte, 1)
	oob := make([]byte, unix.CmsgSpace(4)) // 4 bytes for one int fd

	_, oobn, _, _, err := conn.ReadMsgUnix(buf, oob)
	if err != nil {
		return -1, fmt.Errorf("ReadMsgUnix: %w", err)
	}

	if oobn == 0 {
		return -1, fmt.Errorf("no ancillary data received")
	}

	// Parse the control message.
	msgs, err := unix.ParseSocketControlMessage(oob[:oobn])
	if err != nil {
		return -1, fmt.Errorf("ParseSocketControlMessage: %w", err)
	}

	for _, msg := range msgs {
		if msg.Header.Level != unix.SOL_SOCKET {
			continue
		}
		if msg.Header.Type != unix.SCM_RIGHTS {
			continue
		}

		// Extract the fd(s) from the data. We expect exactly one.
		if len(msg.Data) < 4 {
			return -1, fmt.Errorf("SCM_RIGHTS data too short: %d bytes", len(msg.Data))
		}

		fd := int(binary.LittleEndian.Uint32(msg.Data[:4]))
		return fd, nil
	}

	return -1, fmt.Errorf("no SCM_RIGHTS found in control messages")
}

// SendFD sends a file descriptor over the given UDS connection using SCM_RIGHTS.
// This is used for reverse callbacks (e.g., sending a content URI fd back to Kotlin).
func SendFD(conn *net.UnixConn, fd int) error {
	// We must send at least one byte of regular data.
	buf := []byte{0}
	rights := unix.UnixRights(int(fd))

	_, _, err := conn.WriteMsgUnix(buf, rights, nil)
	if err != nil {
		return fmt.Errorf("WriteMsgUnix: %w", err)
	}
	return nil
}

// RecvFDFromRawConn receives a file descriptor from a raw net.Conn
// by extracting the underlying *os.File and using its fd.
// This is a convenience wrapper for use in the server handler.
func RecvFDFromRawConn(conn net.Conn) (int, error) {
	unixConn, ok := conn.(*net.UnixConn)
	if !ok {
		return -1, fmt.Errorf("connection is not a UnixConn: %T", conn)
	}
	return RecvFD(unixConn)
}

// DupFD duplicates a file descriptor. The caller owns the new fd.
func DupFD(fd int) (int, error) {
	newFd, err := unix.Dup(fd)
	if err != nil {
		return -1, fmt.Errorf("dup: %w", err)
	}
	return newFd, nil
}

// FdToFile wraps an fd in an *os.File. The caller owns the file.
func FdToFile(fd int) *os.File {
	return os.NewFile(uintptr(fd), fmt.Sprintf("fd:%d", fd))
}
