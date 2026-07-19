package app

var appVersionName string
var platformVersion int

func ApplyVersionName(versionName string) {
	appVersionName = versionName
}

func ApplyPlatformVersion(version int) {
	platformVersion = version
}

func PlatformVersion() int {
	return platformVersion
}
