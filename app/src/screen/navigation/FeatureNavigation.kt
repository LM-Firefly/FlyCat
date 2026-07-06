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

package com.github.yumelira.yumebox.screen.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.yumelira.yumebox.WebViewActivity
import com.github.yumelira.yumebox.common.util.DashboardShortcutHelper
import com.github.yumelira.yumebox.feature.substore.presentation.screen.FeatureContent
import com.github.yumelira.yumebox.platform.util.openUrl
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.screen.feature.PanelShortcutDialog
import com.github.yumelira.yumebox.screen.feature.RemoteControllerSection
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
                    onDismiss = { shortcutDialogVisible = false },
                    onConfirm = { name, iconUri ->
                        scope.launch {
                            DashboardShortcutHelper.createPanelShortcut(context, url, name, iconUri)
                        }
                        shortcutDialogVisible = false
                    },
                    onDismissFinished = { shortcutTarget = null },
                )
            }
        },
    )
}
