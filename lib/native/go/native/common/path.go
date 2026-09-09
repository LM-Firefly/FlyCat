// Package common provides shared path resolution utilities for the native bridge.
package common

import "strings"

// ResolveAsRoot resolves a path relative to the application's root data directory.
func ResolveAsRoot(path string) string {
	directories := strings.Split(path, "/")
	result := make([]string, 0, len(directories))

	for _, directory := range directories {
		switch directory {
		case "", ".":
			continue
		case "..":
			if len(result) > 0 {
				result = result[:len(result)-1]
			}
		default:
			result = append(result, directory)
		}
	}

	return strings.Join(result, "/")
}
