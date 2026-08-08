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

package com.github.yumelira.yumebox.feature.substore.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.feature.substore.model.AutoCloseMode
import com.github.yumelira.yumebox.feature.substore.presentation.viewmodel.FeatureViewModel
import com.github.yumelira.yumebox.platform.util.DeviceUtils
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.EnumSelector
import com.github.yumelira.yumebox.presentation.component.NavigationBackIcon
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import tf.gal.yumebox.locale.FlyTxt
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
fun FeatureContent(
    onNavigateBack: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onOpenInAppUrl: (String) -> Unit,
    onCreatePanelShortcut: (url: String, label: String) -> Unit = { _, _ -> },
    topSection: @Composable () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<FeatureViewModel>()
    val isServiceRunning by viewModel.serviceRunningState.collectAsStateWithLifecycle()
    val allowLanAccess by viewModel.allowLanAccess.state.collectAsStateWithLifecycle()
    val frontendPort by viewModel.frontendPort.state.collectAsStateWithLifecycle()
    val backendPort by viewModel.backendPort.state.collectAsStateWithLifecycle()
    val autoCloseMode by viewModel.autoCloseMode.collectAsStateWithLifecycle()

    val host = "127.0.0.1"
    val frontendUrl = "http://${host}:${frontendPort}"
    val backendUrl = "http://${host}:${backendPort}"
    val subStoreUrl = "${frontendUrl}/subs?api=${backendUrl}"

    val isDownloadingSubStoreFrontend by viewModel.isDownloadingSubStoreFrontend.collectAsStateWithLifecycle()
    val isDownloadingSubStoreBackend by viewModel.isDownloadingSubStoreBackend.collectAsStateWithLifecycle()
    val isExtensionInstalled by viewModel.isExtensionInstalled.collectAsStateWithLifecycle()
    val isJavetLoaded by viewModel.isJavetLoaded.collectAsStateWithLifecycle()
    val isSubStoreInitialized by viewModel.isSubStoreInitialized.collectAsStateWithLifecycle()
    val selectedPanelType by viewModel.selectedPanelType.state.collectAsStateWithLifecycle()

    val panelDisplayNames = listOf("Zashboard", "MetaCubeXD", "Yacd")

    LaunchedEffect(Unit) { viewModel.initializeSubStoreStatus() }

    Scaffold(topBar = { TopBar(title = FlyTxt.Feature.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(onNavigateBack = onNavigateBack) }) }) {
        innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item { topSection() }

            item {
                val currentPanelName =
                    panelDisplayNames.getOrElse(selectedPanelType) { FlyTxt.Feature.Panel.Unknown }
                val panelUrl = panelUrlFor(selectedPanelType)

                Title(FlyTxt.Feature.Panel.Section)
                Card {
                    val safeSelectedPanelType =
                        selectedPanelType.coerceIn(0, panelDisplayNames.lastIndex)
                    WindowDropdownPreference(
                        title = FlyTxt.Feature.Panel.SelectPanel,
                        summary = null,
                        items = panelDisplayNames,
                        selectedIndex = safeSelectedPanelType,
                        onSelectedIndexChange = { viewModel.setSelectedPanelType(it) },
                    )

                    ArrowPreference(
                        title = FlyTxt.Feature.Panel.CreateShortcut,
                        summary = null,
                        enabled = panelUrl.isNotBlank(),
                        onClick = { onCreatePanelShortcut(panelUrl, currentPanelName) },
                    )
                }
            }

            item {
                val canStartService = isExtensionInstalled && isSubStoreInitialized
                val serviceStatusSummary = when {
                    isServiceRunning -> FlyTxt.Feature.ServiceStatus.Running.format(frontendUrl)
                    !isExtensionInstalled -> FlyTxt.Feature.ServiceStatus.NeedExtension
                    !isSubStoreInitialized -> FlyTxt.Feature.ServiceStatus.NeedSubStore
                    else -> FlyTxt.Feature.ServiceStatus.NotRunning
                }
                Title(FlyTxt.Feature.ServiceStatus.Section)
                Card {
                    val autoCloseItems = remember { AutoCloseMode.entries.map { it.getDisplayName() } }
                    val autoCloseValues = AutoCloseMode.entries

                    EnumSelector(
                        title = FlyTxt.Feature.ServiceStatus.SwitchStartSubStore,
                        summary = FlyTxt.Feature.ServiceStatus.AutoCloseModeSummary,
                        currentValue = autoCloseMode,
                        items = autoCloseItems,
                        values = autoCloseValues,
                        onValueChange = viewModel::setAutoCloseMode,
                    )
                    SwitchPreference(
                        title = FlyTxt.Feature.ServiceStatus.AllowLan,
                        summary = FlyTxt.Feature.ServiceStatus.AllowLanSummary,
                        checked = allowLanAccess,
                        onCheckedChange = { viewModel.setAllowLanAccess(it) },
                    )
                    ArrowPreference(
                        title = FlyTxt.Feature.SubStore.Title,
                        summary = if (isServiceRunning) subStoreUrl else serviceStatusSummary,
                        enabled = !DeviceUtils.is32BitDevice() && isServiceRunning,
                        onClick = {
                            if (!isServiceRunning) return@ArrowPreference
                            onOpenInAppUrl(subStoreUrl)
                        },
                    )
                }
            }

            item {
                Title(FlyTxt.Feature.SubStore.SectionHint)
                Card {
                    ArrowPreference(
                        title =
                            if (isExtensionInstalled) {
                                FlyTxt.Feature.SubStore.ExtensionInstalled
                            } else {
                                FlyTxt.Feature.SubStore.ExtensionInstall
                            },
                        summary =
                            when {
                                isExtensionInstalled && isJavetLoaded ->
                                    FlyTxt.Feature.SubStore.JavetAvailable
                                isExtensionInstalled -> FlyTxt.Feature.SubStore.JavetPending
                                else -> FlyTxt.Feature.SubStore.DownloadHint
                            },
                        onClick = {
                            if (!isExtensionInstalled) {
                                onOpenExternalUrl(
                                    "https://github.com/LM-Firefly/FlyCat/releases/tag/Extension"
                                )
                            } else {
                                viewModel.refreshExtensionStatus()
                            }
                        },
                    )
                    ArrowPreference(
                        title = FlyTxt.Feature.SubStore.DownloadResources,
                        summary = FlyTxt.Feature.SubStore.DownloadResourcesSummary,
                        onClick = { viewModel.downloadSubStoreAll() },
                        enabled = !isDownloadingSubStoreFrontend && !isDownloadingSubStoreBackend,
                    )
                }
            }
        }
    }
}

private fun panelUrlFor(panelType: Int): String =
    when (panelType) {
        0 -> "https://board.zash.run.place"
        1 -> "https://metacubex.github.io/metacubexd"
        2 -> "https://yacd.haishan.me"
        else -> "https://board.zash.run.place"
    }
