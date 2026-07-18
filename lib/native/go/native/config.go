package main

//#include "bridge.h"
import "C"

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"
	"unsafe"

	"cfa/native/app"
	"cfa/native/config"
	"cfa/native/tunnel"

	"github.com/metacubex/mihomo/hub"
	mlog "github.com/metacubex/mihomo/log"
	"gopkg.in/yaml.v3"
)

const loadCompiledRawTimeout = 30 * time.Second

type ageKeyPair struct {
	SecretKey string `json:"secretKey"`
	PublicKey string `json:"publicKey"`
}

type inspectResult struct {
	Success bool   `json:"success"`
	Payload string `json:"payload"`
	Error   string `json:"error"`
}

type remoteValidCallback struct {
	callback unsafe.Pointer
}

func (r *remoteValidCallback) reportStatus(json string) {
	C.fetch_report(r.callback, marshalString(json))
}

//export fetchAndValid
func fetchAndValid(callback unsafe.Pointer, path, url C.c_string, force C.int) {
	go func(path, url string, callback unsafe.Pointer) {
		cb := &remoteValidCallback{callback: callback}

		err := config.FetchAndValid(path, url, force != 0, cb.reportStatus)

		C.fetch_complete(callback, marshalString(err))

		C.release_object(callback)
	}(C.GoString(path), C.GoString(url), callback)
}

//export loadCompiledRaw
func loadCompiledRaw(completable unsafe.Pointer, configRawJSON *C.char) {
	rawCopy := C.GoString(configRawJSON)
	C.free(unsafe.Pointer(configRawJSON))
	go func(raw string) {
		var done sync.Once
		finish := func(errMsg *string) {
			done.Do(func() {
				if errMsg == nil {
					C.complete(completable, nil)
				} else {
					C.complete(completable, marshalString(*errMsg))
				}
				C.release_object(completable)
			})
		}

		watchdog := time.AfterFunc(loadCompiledRawTimeout, func() {
			err := "loadCompiledRaw timeout (>30s)"
			mlog.Errorln("[BRIDGE]", err)
			finish(&err)
		})
		defer watchdog.Stop()

		defer func() {
			if r := recover(); r != nil {
				err := fmt.Sprintf("loadCompiledRaw panic: %v", r)
				mlog.Errorln("[BRIDGE]", err)
				finish(&err)
			}
		}()

		mlog.Infoln("[BRIDGE] loadCompiledRaw begin")

		rawCfg, cfg, err := config.ParseCompiledRaw(raw)
		if err != nil {
			errMsg := err.Error()
			mlog.Errorln("[BRIDGE] loadCompiledRaw parse failed:", errMsg)
			finish(&errMsg)
			return
		}
		mlog.Infoln("[BRIDGE] loadCompiledRaw parsed, applying config")
		hub.ApplyConfig(cfg)
		tunnel.IncrProxyGroupVersion()
		mlog.Infoln("[BRIDGE] loadCompiledRaw apply done, complete")
		app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)
		finish(nil)
		mlog.Infoln("[BRIDGE] loadCompiledRaw complete sent")
	}(rawCopy)
}

//export loadCompiledRawSync
func loadCompiledRawSync(configRawJSON *C.char) *C.char {
	rawCopy := C.GoString(configRawJSON)
	C.free(unsafe.Pointer(configRawJSON))

	rawCfg, cfg, err := config.ParseCompiledRaw(rawCopy)
	if err != nil {
		return marshalString(err.Error())
	}

	hub.ApplyConfig(cfg)
	tunnel.IncrProxyGroupVersion()
	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)
	return nil
}

//export inspectCompiledGroupsResult
func inspectCompiledGroupsResult(configRawJSON C.c_string, profileDir C.c_string, excludeNotSelectable C.int) *C.char {
	groups, err := config.QueryProxyGroupsFromCompiledRaw(
		C.GoString(configRawJSON),
		C.GoString(profileDir),
		excludeNotSelectable != 0,
	)
	if err != nil {
		return marshalJSON(inspectResult{Success: false, Error: err.Error()})
	}
	payload, err := yaml.Marshal(groups)
	if err != nil {
		return marshalJSON(inspectResult{Success: false, Error: err.Error()})
	}
	return marshalJSON(inspectResult{Success: true, Payload: string(payload)})
}

//export inspectCompiledGroupNames
func inspectCompiledGroupNames(configRawJSON C.c_string, excludeNotSelectable C.int) *C.char {
	names, err := config.QueryProxyGroupNamesFromCompiledRaw(
		C.GoString(configRawJSON),
		excludeNotSelectable != 0,
	)
	if err != nil {
		return nil
	}
	payload, err := json.Marshal(names)
	if err != nil {
		return nil
	}
	return C.CString(string(payload))
}

//export setAgeSecretKey
func setAgeSecretKey(key C.c_string) {
	if key == nil {
		config.SetAgeSecretKey("")
		config.SetGlobalSecretKeys()
		return
	}

	k := C.GoString(key)
	config.SetAgeSecretKey(k)
	config.SetGlobalSecretKeys(k)
}

//export genX25519KeyPair
func genX25519KeyPair() *C.char {
	secretKey, publicKey, err := config.GenX25519KeyPair()
	if err != nil {
		return nil
	}

	return marshalJSON(ageKeyPair{SecretKey: secretKey, PublicKey: publicKey})
}

//export genHybridKeyPair
func genHybridKeyPair() *C.char {
	secretKey, publicKey, err := config.GenHybridKeyPair()
	if err != nil {
		return nil
	}

	return marshalJSON(ageKeyPair{SecretKey: secretKey, PublicKey: publicKey})
}

//export verifySecretKeys
func verifySecretKeys(secretKeys C.c_string) C.int {
	if config.VerifySecretKeys(C.GoString(secretKeys)) != nil {
		return 0
	}

	return 1
}

//export toPublicKeys
func toPublicKeys(secretKeys C.c_string) *C.char {
	publicKeys, err := config.ToPublicKeys(C.GoString(secretKeys))
	if err != nil {
		return nil
	}

	return marshalJSON(publicKeys)
}

//export verifyPublicKeys
func verifyPublicKeys(publicKeys C.c_string) C.int {
	if config.VerifyPublicKeys(C.GoString(publicKeys)) != nil {
		return 0
	}

	return 1
}
