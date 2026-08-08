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

package com.github.yumelira.yumebox.core

/**
 * Shared holder for app build config values.
 * Initialized in Application.onCreate to allow feature modules to access version info without depending on the app module's BuildConfig.
 */
object BuildConfigHolder {
    lateinit var versionName: String
        private set

    lateinit var applicationId: String
        private set

    var uiBuildId: String = ""
        private set

    var kernelGitVersion: String = ""
        private set

    fun init(versionName: String, applicationId: String, uiBuildId: String = "", kernelGitVersion: String = "") {
        this.versionName = versionName
        this.applicationId = applicationId
        this.uiBuildId = uiBuildId
        this.kernelGitVersion = kernelGitVersion
    }
}
