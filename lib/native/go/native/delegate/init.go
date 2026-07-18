// Package delegate initializes the mihomo core and registers native socket hooks.
package delegate

import (
	"errors"
	"fmt"
	"math"
	"strings"
	"syscall"

	"github.com/metacubex/mihomo/component/process"
	"github.com/metacubex/mihomo/log"

	"cfa/native/app"
	"cfa/native/platform"

	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/constant"
)

var errBlocked = errors.New("blocked")

// Init initializes the mihomo core with the given home directory, version info, and Android SDK version.
func Init(home, versionName, gitVersion string, platformVersion int) {
	log.Infoln("Init core, home: %s, versionName: %s, gitVersion: %s, platformVersion: %d", home, versionName, gitVersion, platformVersion)
	constant.SetHomeDir(home)
	// gitVersion = ${CURRENT_BRANCH}_${COMMIT_HASH}_${COMPILE_TIME}
	if versions := strings.Split(gitVersion, "_"); len(versions) == 3 {
		constant.Version = fmt.Sprintf("%s-%s-%s", strings.ToLower(versions[0]), versions[1], strings.ToLower(versionName))
		constant.BuildTime = versions[2]
	} else {
		constant.Version = gitVersion
	}
	constant.Version = strings.ToLower(constant.Version)
	app.ApplyVersionName(versionName)
	app.ApplyPlatformVersion(platformVersion)

	process.DefaultPackageNameResolver = func(metadata *constant.Metadata) (string, error) {
		src, dst := metadata.RawSrcAddr, metadata.RawDstAddr

		if src == nil || dst == nil {
			return "", process.ErrInvalidNetwork
		}

		owner := app.QuerySocketOwner(metadata.RawSrcAddr, metadata.RawDstAddr)
		if owner.UID >= 0 && uint(owner.UID) <= math.MaxUint32 {
			metadata.Uid = uint32(owner.UID)
		}

		log.Debugln("[PKG] %s --> %s by %d[%s]", metadata.SourceAddress(), metadata.RemoteAddress(), owner.UID, owner.Package)

		return owner.Package, nil
	}

	dialer.DefaultSocketHook = func(_, _ string, conn syscall.RawConn) error {
		if platform.ShouldBlockConnection() {
			return errBlocked
		}

		return conn.Control(func(fd uintptr) {
			app.MarkSocket(int(fd))
		})
	}
}
