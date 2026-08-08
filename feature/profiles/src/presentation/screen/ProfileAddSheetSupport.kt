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

package com.github.yumelira.yumebox.feature.profiles.presentation.screen

import androidx.compose.runtime.MutableState
import com.github.yumelira.yumebox.feature.profiles.presentation.viewmodel.ProfilesViewModel

internal fun dismissProfileAddSheet(
    show: MutableState<Boolean>,
    isDownloading: Boolean,
    profilesViewModel: ProfilesViewModel,
) {
    if (isDownloading) return
    show.value = false
    profilesViewModel.clearDownloadProgress()
}
