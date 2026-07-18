package app

import (
	"errors"
	"os"
	"syscall"
)

var openContentImpl = func(_ string) (int, error) {
	return -1, errors.New("not implement")
}

// OpenContent opens a content URI and returns a temporary file descriptor.
func OpenContent(url string) (*os.File, error) {
	fd, err := openContentImpl(url)

	if err != nil {
		return nil, err
	}

	_ = syscall.SetNonblock(fd, true)

	return os.NewFile(uintptr(fd), "fd"), nil
}

// ApplyContentContext registers the platform-specific content URI opener.
func ApplyContentContext(openContent func(string) (int, error)) {
	openContentImpl = openContent
}
