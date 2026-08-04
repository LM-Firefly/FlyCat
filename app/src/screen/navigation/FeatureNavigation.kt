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

@file:Suppress("FunctionName")

package com.github.yumeyucca.yumebox.screen.navigation


import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.github.yumeyucca.yumebox.WebViewActivity
import com.github.yumeyucca.yumebox.common.util.DashboardShortcutHelper
import com.github.yumeyucca.yumebox.common.util.openUrl
import com.github.yumeyucca.yumebox.presentation.component.Navigator
import com.github.yumeyucca.yumebox.presentation.screen.FeatureContent
import com.github.yumeyucca.yumebox.screen.feature.PanelShortcutDialog
import com.github.yumeyucca.yumebox.screen.feature.RemoteControllerSection
import com.github.yumeyucca.yumebox.screen.settings.backup.BackupRestoreSection
import kotlinx.coroutines.launch

@Composable
fun FeatureScreen(navigator: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var shortcutTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var shortcutDialogVisible by remember { mutableStateOf(false) }

    FeatureContent(
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
        bottomSection = {
            BackupRestoreSection()
        },
    )
}
