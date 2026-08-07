/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 */

package com.github.yumeyucca.yumebox.core.util

import android.content.Context

class BuiltinGeoAssetsRequiredException : IllegalStateException()

private val builtinGeoAssetNames =
    listOf(
        "geoip.metadb",
        "geosite.dat",
        "ASN.mmdb",
        "BundleMRS.7z",
    )

/** Blocks local core startup until all bundled Geo assets have been installed once. */
fun Context.requireBuiltinGeoAssets() {
    check(
        builtinGeoAssetNames.all { expectedName ->
            runtimeHomeDir.listFiles()?.any { file ->
                file.isFile &&
                    file.length() > 0L &&
                    expectedName.equals(file.name, ignoreCase = true)
            }
                ?: false
        }
    ) {
        throw BuiltinGeoAssetsRequiredException()
    }
}
