// Package main implements the native bridge for the FlyCat Android application.
package main

import "C"

import (
	"encoding/json"
	"reflect"
	"unsafe"

	"github.com/metacubex/mihomo/log"
)

// cStringFromBytes copies a byte slice into a null-terminated C string
// without the intermediate Go string allocation that C.CString requires.
func cStringFromBytes(b []byte) *C.char {
	n := len(b)
	if n == 0 {
		return C.CString("")
	}
	buf := C.malloc(C.size_t(n + 1))
	copy(unsafe.Slice((*byte)(buf), n), b)
	*(*byte)(unsafe.Add(buf, n)) = 0
	return (*C.char)(buf)
}

func marshalJSON(obj any) *C.char {
	res, err := json.Marshal(obj)
	if err != nil {
		log.Errorln("marshalJSON: %v", err)
		return nil
	}

	return cStringFromBytes(res)
}

func marshalString(obj any) *C.char {
	if obj == nil {
		return nil
	}

	switch o := obj.(type) {
	case error:
		return C.CString(o.Error())
	case string:
		return C.CString(o)
	}

	log.Errorln("marshalString: invalid type %s", reflect.TypeOf(obj).Name())
	return nil
}
