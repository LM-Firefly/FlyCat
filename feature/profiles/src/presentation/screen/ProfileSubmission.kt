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
 */

package com.github.yumelira.yumebox.feature.profiles.presentation.screen

import android.net.Uri
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.presentation.util.PROFILE_IMPORT_TYPE_FILE
import com.github.yumelira.yumebox.presentation.util.PROFILE_IMPORT_TYPE_QR
import com.github.yumelira.yumebox.presentation.util.PROFILE_IMPORT_TYPE_URL
import dev.oom_wg.purejoy.mlang.MLang
import java.util.UUID

internal typealias AddProfile = (String, String, Profile.Type, Long, Uri?, String) -> Unit
internal typealias UpdateProfile = (UUID, String, String, Long, String?) -> Unit

internal data class ProfileDraft(
    val typeIndex: Int,
    val name: String,
    val url: String,
    val filePath: String,
    val ageSecretKey: String,
    val profileToEdit: Profile?,
    val isDownloading: Boolean,
)

internal class ProfileSubmissionActions(
    val hideKeyboard: () -> Unit,
    val clearError: () -> Unit,
    val startDownload: () -> Unit,
    val showError: (String) -> Unit,
    val addProfile: AddProfile,
    val updateProfile: UpdateProfile,
)

context(actions: ProfileSubmissionActions)
internal fun submitProfile(draft: ProfileDraft) {
    if (draft.typeIndex == PROFILE_IMPORT_TYPE_QR || draft.isDownloading) return
    if (draft.typeIndex == PROFILE_IMPORT_TYPE_URL && draft.url.isBlank()) {
        actions.showError(MLang.ProfilesPage.Validation.EnterUrl)
        return
    }
    if (draft.typeIndex == PROFILE_IMPORT_TYPE_FILE && draft.filePath.isBlank()) {
        actions.showError(MLang.ProfilesPage.Validation.SelectFile)
        return
    }

    actions.hideKeyboard()
    actions.clearError()
    actions.startDownload()
    val profile = draft.profileToEdit
    if (profile != null) {
        val source = if (draft.typeIndex == PROFILE_IMPORT_TYPE_URL) draft.url else profile.source
        actions.updateProfile(profile.uuid, draft.name, source, profile.interval, draft.ageSecretKey.trim())
    } else if (draft.typeIndex == PROFILE_IMPORT_TYPE_URL) {
        actions.addProfile(
            draft.name.ifBlank { MLang.ProfilesPage.Input.NewProfile },
            draft.url,
            Profile.Type.Url,
            0L,
            null,
            draft.ageSecretKey.trim(),
        )
    } else {
        actions.addProfile(
            draft.name.ifBlank { MLang.ProfilesPage.Input.NewProfile },
            draft.filePath,
            Profile.Type.File,
            0L,
            Uri.parse(draft.filePath),
            draft.ageSecretKey.trim(),
        )
    }
}
