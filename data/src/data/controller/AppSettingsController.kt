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

package com.github.yumelira.yumebox.data.controller

import com.github.yumelira.yumebox.core.contract.AppSettingsControllerContract
import com.github.yumelira.yumebox.core.contract.LanguageApplier
import com.github.yumelira.yumebox.core.model.AppLanguage
import com.github.yumelira.yumebox.data.store.AppStateManager

class AppSettingsController(
    private val appStateManager: AppStateManager,
    private val languageApplier: LanguageApplier = LanguageApplier {},
    private val applyUserAgent: (String) -> Unit = {},
) : AppSettingsControllerContract {
    private val store = appStateManager.appSettingsStore
    override fun applyAppLanguage(language: AppLanguage) {
        store.appLanguage.set(language)
        languageApplier.apply(language)
    }

    override fun applyCustomUserAgent(userAgent: String) {
        store.customUserAgent.set(userAgent)
        applyUserAgent(userAgent)
    }
}
