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

@file:Suppress("UnusedSymbol")

package com.github.yumeyucca.yumebox.runtime.service.root


import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import tf.gal.yumebox.locale.YumeTxt

data class RootAccessStatus(val rootAccessGranted: Boolean) {
    val canStartRoot: Boolean
        get() = rootAccessGranted

    fun rootBlockedMessage(): String = YumeTxt.NetworkSettings.Error.RootRequired
}

object RootAccessSupport {
    fun evaluate(@Suppress("UNUSED_PARAMETER") context: Context): RootAccessStatus {
        val rootAccessGranted = RootPackageShell.hasRootAccess()
        return RootAccessStatus(rootAccessGranted = rootAccessGranted)
    }

    // tryResume/completeResume (internal API): the libsu shell callback may fire after the
    // caller was cancelled, so the resume must be race-safe rather than throw.
    @OptIn(InternalCoroutinesApi::class)
    suspend fun evaluateAsync(@Suppress("UNUSED_PARAMETER") context: Context): RootAccessStatus =
        suspendCancellableCoroutine { continuation ->
            fun resume(rootAccessGranted: Boolean) {
                val status = RootAccessStatus(rootAccessGranted = rootAccessGranted)
                val resumeToken = continuation.tryResume(status) ?: return
                continuation.completeResume(resumeToken)
            }

            runCatching {
                Shell.getShell(Shell.EXECUTOR) { shell ->
                    resume(runCatching { shell.isRoot }.getOrDefault(false))
                }
            }
                .onFailure { resume(false) }
        }

    suspend fun requireRootAccess(context: Context): RootAccessStatus =
        evaluateAsync(context).also { status ->
            check(status.canStartRoot) { status.rootBlockedMessage() }
        }
}
