package golang

import (
	// Blank import pulls the whole cfa native bridge (cfa/native/all) into this
	// wrapper module so a plain `go build` here type-checks the bridge against
	// the mihomo sources tracked in this directory.
	_ "cfa/native/all"
)
