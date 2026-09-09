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

package com.github.lmfirefly.flycat.runtime.api.constants

import android.content.ComponentName
import com.github.lmfirefly.flycat.core.Global

/**
 * Activity entry points consumed by runtime notifications/tiles. Injected by the app module
 * during Application.onCreate — the runtime layer must not hardcode app class names, and the
 * app process always initializes before any of these consumers run.
 */
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
