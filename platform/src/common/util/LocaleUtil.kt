/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
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
 *
 */

package com.github.yumelira.yumebox.platform.util

import java.util.Locale

object LocaleUtil {
    private val NORMALIZED_REGION_CODES = setOf("TW")

    @Volatile private var override: Locale? = null

    fun setCurrentLocale(locale: Locale?) {
        override = locale
    }

    fun currentLocale(): Locale = override ?: Locale.getDefault()

    fun isChineseLocale(): Boolean = currentLocale().language == "zh"

    fun normalizeRegionCode(countryCode: String?): String? {
        if (countryCode == null || !isChineseLocale()) return countryCode
        return if (countryCode.uppercase() in NORMALIZED_REGION_CODES) "CN" else countryCode
    }

    fun normalizeFlagUrl(
        countryCode: String,
        baseUrl: String = "https://hatscripts.github.io/circle-flags/flags/",
    ): String {
        val code =
            if (isChineseLocale() && countryCode.uppercase() in NORMALIZED_REGION_CODES) {
                "cn"
            } else {
                countryCode.lowercase()
            }
        return "$baseUrl$code.svg"
    }
}
