/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * YumeBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

package com.github.yumelira.yumebox.runtime.service.core

import android.content.Context
import java.io.File

/** Resolves the raw PIE shell and the Go shared library extracted by the release payload loader. */
internal object CoreArtifacts {
    const val SHELL_NAME = "libmihomo.so"
    const val LIBRARY_NAME = "libmihomocore.so"
    const val LIBRARY_OPTION = "--core-library"
    @Volatile private var cachedLibrary: File? = null

    fun shell(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, SHELL_NAME)

    fun library(context: Context): File {
        cachedLibrary?.takeIf(File::isFile)?.let { return it }
        return synchronized(this) {
            cachedLibrary?.takeIf(File::isFile)?.let { return@synchronized it }

            val unpacked = File(context.applicationInfo.nativeLibraryDir, LIBRARY_NAME)
            if (unpacked.isFile) {
                cachedLibrary = unpacked
                return@synchronized unpacked
            }

            val packed =
                runCatching {
                        val bridge =
                            Class.forName("dev.yume.loader.PayloadRuntime", false, context.classLoader)
                        bridge
                            .getMethod("findNativeLibrary", String::class.java)
                            .invoke(null, LIBRARY_NAME) as? String
                    }
                    .getOrNull()
                    ?.let(::File)
                    ?.takeIf(File::isFile)
                    ?: error("mihomo core library is unavailable")
            cachedLibrary = packed
            packed
        }
    }

    fun arguments(context: Context, coreArgs: Array<String>): Array<String> =
        arrayOf(LIBRARY_OPTION, library(context).absolutePath, *coreArgs)
}
