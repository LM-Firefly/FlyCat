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

package com.github.yumelira.yumebox.runtime.api.service.common.constants

import android.content.ComponentName
import com.github.yumelira.yumebox.core.Global

object Components {
    @Volatile
    private var mainActivityClassName: String? = null

    @Volatile
    private var proxySheetActivityClassName: String? = null

    val MAIN_ACTIVITY: ComponentName
        get() = ComponentName(Global.application.packageName, requireMain())

    val PROXY_SHEET_ACTIVITY: ComponentName
        get() = ComponentName(Global.application.packageName, requireSheet())

    fun registerMainActivity(className: String) {
        mainActivityClassName = className
    }

    fun registerProxySheetActivity(className: String) {
        proxySheetActivityClassName = className
    }

    fun register(mainActivityClassName: String, proxySheetActivityClassName: String) {
        registerMainActivity(mainActivityClassName)
        registerProxySheetActivity(proxySheetActivityClassName)
    }

    private fun requireMain(): String = mainActivityClassName
        ?: error("Components.MAIN_ACTIVITY not registered. Call Components.register(...) in Application.onCreate.")

    private fun requireSheet(): String = proxySheetActivityClassName
        ?: error("Components.PROXY_SHEET_ACTIVITY not registered. Call Components.register(...) in Application.onCreate.")
}
