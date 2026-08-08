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

import android.app.Application
import android.content.Context
import android.net.Uri
import com.github.yumelira.yumebox.core.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID

internal fun importedProfileDir(context: Context, profile: Profile): File =
    context.filesDir.importedProfileDir(profile.uuid)

internal fun importedConfigFile(context: Context, profile: Profile): File = importedProfileDir(context, profile).resolve("config.yaml")

internal suspend fun Application.copyProfileImport(uri: Uri, uuid: UUID) {
    withContext(Dispatchers.IO) {
        val outputFile = filesDir.importedProfileDir(uuid).resolve("config.yaml")
        outputFile.parentFile?.mkdirs()
        contentResolver.openInputStream(uri)?.use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Failed to open file: $uri")
        Timber.d("File copied: ${outputFile.absolutePath}")
    }
}

private fun File.importedProfileDir(uuid: UUID): File = resolve("imported").resolve(uuid.toString())
