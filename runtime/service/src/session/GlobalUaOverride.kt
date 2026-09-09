/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.runtime.service.session

import com.github.lmfirefly.flycat.core.model.OverrideSpec
import com.tencent.mmkv.MMKV
import java.io.File

/**
 * Injects app-level `global-ua` into the compile override chain so core provider refresh uses the
 * same User-Agent as Kotlin subscription downloads. Always appended last so it beats subscription
 * and user override values; not gated by disable-all.
 */
object GlobalUaOverride {
    const val FILE_NAME = "__global_ua_override__.yaml"
    private const val SETTINGS_STORE_ID = "settings"
    private const val CUSTOM_USER_AGENT_KEY = "customUserAgent"

    fun buildYaml(userAgent: String = resolveUserAgent()): String {
        if (userAgent.isBlank()) return ""
        val escaped =
            userAgent
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
        return "global-ua: \"$escaped\"\n"
    }

    fun materialize(
        dir: File,
        userAgent: String = resolveUserAgent(),
    ): OverrideSpec {
        dir.mkdirs()
        val file = File(dir, FILE_NAME)
        file.writeText(buildYaml(userAgent))
        return OverrideSpec(path = file.absolutePath, ext = "yaml")
    }

    private fun resolveUserAgent(): String {
        val settings = MMKV.mmkvWithID(SETTINGS_STORE_ID, MMKV.MULTI_PROCESS_MODE)
        return settings.decodeString(CUSTOM_USER_AGENT_KEY, "").orEmpty()
    }
}
