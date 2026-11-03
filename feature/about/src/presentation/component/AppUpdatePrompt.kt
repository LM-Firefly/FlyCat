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

package com.github.lmfirefly.flycat.feature.about.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.feature.about.GitHubUpdateViewModel
import com.github.lmfirefly.flycat.feature.about.UpdateCandidate
import com.github.lmfirefly.flycat.feature.about.presentation.screen.UpdateCandidateDialog
import com.github.lmfirefly.flycat.presentation.util.toast
import org.koin.androidx.compose.koinViewModel

/**
 * 全局「发现新版本」提示：自动检查发现新版本后弹出更新弹窗（复用关于页的 [UpdateCandidateDialog]）。
 * 挂载在主界面一次即可；共享单例 [GitHubUpdateViewModel] 的自动检查候选流，与关于页手动检查互不干扰。
 */
@Composable
fun AppUpdatePrompt() {
    val viewModel = koinViewModel<GitHubUpdateViewModel>()
    val context = LocalContext.current
    val autoCandidate by viewModel.autoCheckCandidate.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dialogCandidate by remember { mutableStateOf<UpdateCandidate?>(null) }
    LaunchedEffect(autoCandidate) { autoCandidate?.let { dialogCandidate = it } }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            context.toast(message)
            viewModel.consumeMessage()
        }
    }
    // 下载完成（isDownloading 由 true 转 false）后关闭弹窗。
    var wasDownloading by remember { mutableStateOf(false) }
    LaunchedEffect(downloadProgress.isDownloading) {
        if (wasDownloading && !downloadProgress.isDownloading) dialogCandidate = null
        wasDownloading = downloadProgress.isDownloading
    }
    UpdateCandidateDialog(
        candidate = dialogCandidate,
        downloadProgressFlow = viewModel.downloadProgress,
        onDismiss = {
            dialogCandidate = null
            viewModel.dismissAutoCheckCandidate()
        },
        onDownload = viewModel::downloadAndInstall,
        onCancelDownload = viewModel::cancelDownload,
    )
}
