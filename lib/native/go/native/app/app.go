// Package app provides Android application context and platform utilities for the native bridge.
package app

import "time"

var appVersionName string
var platformVersion int

// ApplyVersionName stores the application version name for later retrieval.
func ApplyVersionName(versionName string) {
	appVersionName = versionName
}

// ApplyPlatformVersion stores the Android SDK version for later retrieval.
func ApplyPlatformVersion(version int) {
	platformVersion = version
}

// VersionName returns the stored application version name.
func VersionName() string {
	return appVersionName
}

// PlatformVersion returns the stored Android SDK version.
func PlatformVersion() int {
	return platformVersion
}

// NotifyTimeZoneChanged notifies the runtime of a system timezone change.
func NotifyTimeZoneChanged(name string, offset int) {
	time.Local = time.FixedZone(name, offset)
}
