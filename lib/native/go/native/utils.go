package main

import "C"

import (
	"encoding/json"
	"reflect"

	"github.com/metacubex/mihomo/log"
)

func marshalJSON(obj any) *C.char {
	res, err := json.Marshal(obj)
	if err != nil {
		log.Errorln("marshalJSON: %v", err)
		return nil
	}

	return C.CString(string(res))
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
