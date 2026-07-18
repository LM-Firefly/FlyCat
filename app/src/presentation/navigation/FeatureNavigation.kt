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

package com.github.lmfirefly.flycat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.lmfirefly.flycat.R
import com.github.lmfirefly.flycat.WebViewActivity
import com.github.lmfirefly.flycat.common.util.DashboardShortcutUtils
import com.github.lmfirefly.flycat.feature.substore.presentation.component.PanelShortcutDialog
import com.github.lmfirefly.flycat.feature.substore.presentation.screen.FeatureContent
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.RemoteControllerSection
import com.github.lmfirefly.flycat.ui.platform.openUrl
import kotlinx.coroutines.launch

@Composable
fun FeatureScreen(navigator: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var shortcutTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var shortcutDialogVisible by remember { mutableStateOf(false) }

    FeatureContent(
        onNavigateBack = { navigator.pop() },
        onOpenExternalUrl = { url -> openUrl(context, url) },
        onOpenInAppUrl = { url -> WebViewActivity.start(context, url) },
        onCreatePanelShortcut = { url, label ->
            shortcutTarget = url to label
            shortcutDialogVisible = true
        },
        topSection = {
            RemoteControllerSection()
            shortcutTarget?.let { (url, label) ->
                PanelShortcutDialog(
                    show = shortcutDialogVisible,
                    url = url,
                    defaultLabel = label,
                    defaultIconResId = R.mipmap.ic_launcher_foreground,
                    onDismiss = { shortcutDialogVisible = false },
                    onConfirm = { name, iconUri ->
                        scope.launch {
                            DashboardShortcutUtils.createPanelShortcut(context, url, name, iconUri)
                        }
                        shortcutDialogVisible = false
                    },
                    onDismissFinished = { shortcutTarget = null },
                )
            }
        },
    )
}
