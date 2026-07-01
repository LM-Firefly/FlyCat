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

package com.github.yumelira.yumebox.feature.substore.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.core.model.LinkOpenMode
import com.github.yumelira.yumebox.feature.substore.model.AutoCloseMode
import com.github.yumelira.yumebox.feature.substore.presentation.viewmodel.FeatureViewModel
import com.github.yumelira.yumebox.platform.util.DeviceUtil
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.EnumSelector
import com.github.yumelira.yumebox.presentation.component.NavigationBackIcon
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
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
    val panelOpenMode by viewModel.panelOpenMode.state.collectAsStateWithLifecycle()

    val panelDisplayNames = listOf("Zashboard", "MetaCubeXD", "Yacd")

    LaunchedEffect(Unit) { viewModel.initializeSubStoreStatus() }

    Scaffold(topBar = { TopBar(title = MLang.Feature.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(onNavigateBack = onNavigateBack) }) }) {
        innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item { topSection() }

            item {
                val currentPanelName =
                    panelDisplayNames.getOrElse(selectedPanelType) { MLang.Feature.Panel.Unknown }
                val panelUrl = panelUrlFor(selectedPanelType)

                Title(MLang.Feature.Panel.Section)
                Card {
                    val safeSelectedPanelType =
                        selectedPanelType.coerceIn(0, panelDisplayNames.lastIndex)
                    WindowDropdownPreference(
                        title = MLang.Feature.Panel.SelectPanel,
                        summary = null,
                        items = panelDisplayNames,
                        selectedIndex = safeSelectedPanelType,
                        onSelectedIndexChange = { viewModel.setSelectedPanelType(it) },
                    )

                    ArrowPreference(
                        title = MLang.Feature.Panel.CreateShortcut,
                        summary = null,
                        enabled = panelUrl.isNotBlank(),
                        onClick = { onCreatePanelShortcut(panelUrl, currentPanelName) },
                    )
                }
            }

            item {
                val canStartService = isExtensionInstalled && isSubStoreInitialized
                val serviceStatusSummary = when {
                    isServiceRunning -> MLang.Feature.ServiceStatus.Running.format(frontendUrl)
                    !isExtensionInstalled -> MLang.Feature.ServiceStatus.NeedExtension
                    !isSubStoreInitialized -> MLang.Feature.ServiceStatus.NeedSubStore
                    else -> MLang.Feature.ServiceStatus.NotRunning
                }
                Title(MLang.Feature.ServiceStatus.Section)
                Card {
                    val autoCloseItems = AutoCloseMode.entries.map { it.getDisplayName() }
                    val autoCloseValues = AutoCloseMode.entries

                    EnumSelector(
                        title = MLang.Feature.ServiceStatus.SwitchStartSubStore,
                        summary = MLang.Feature.ServiceStatus.AutoCloseModeSummary,
                        currentValue = autoCloseMode,
                        items = autoCloseItems,
                        values = autoCloseValues,
                        onValueChange = viewModel::setAutoCloseMode,
                    )
                    SwitchPreference(
                        title = MLang.Feature.ServiceStatus.AllowLan,
                        summary = MLang.Feature.ServiceStatus.AllowLanSummary,
                        checked = allowLanAccess,
                        onCheckedChange = { viewModel.setAllowLanAccess(it) },
                    )
                    ArrowPreference(
                        title = "Sub-Store",
                        summary = if (isServiceRunning) subStoreUrl else serviceStatusSummary,
                        enabled = !DeviceUtil.is32BitDevice() && isServiceRunning,
                        onClick = {
                            if (!isServiceRunning) return@ArrowPreference
                            onOpenInAppUrl(subStoreUrl)
                        },
                    )
                }
            }

            item {
                val currentPanelName =
                    panelDisplayNames.getOrElse(selectedPanelType) { MLang.Feature.Panel.Unknown }
                val panelUrl = panelUrlFor(selectedPanelType)
                val panelOpenModeItems =
                    listOf(
                        MLang.ProfilesPage.LinkSettings.OpenModeInApp,
                        MLang.ProfilesPage.LinkSettings.OpenModeExternal,
                    )
                val panelOpenModeIndex =
                    when (panelOpenMode) {
                        LinkOpenMode.IN_APP -> 0
                        LinkOpenMode.EXTERNAL_BROWSER -> 1
                    }

                Title(MLang.Feature.Panel.Section)
                Card {
                    val safeSelectedPanelType =
                        selectedPanelType.coerceIn(0, panelDisplayNames.lastIndex)
                    WindowDropdownPreference(
                        title = MLang.Feature.Panel.SelectPanel,
                        summary =
                            panelDisplayNames.getOrElse(safeSelectedPanelType) {
                                panelDisplayNames.first()
                            },
                        items = panelDisplayNames,
                        selectedIndex = safeSelectedPanelType,
                        onSelectedIndexChange = { viewModel.setSelectedPanelType(it) },
                    )

                    BasicComponent(
                        title = "URL",
                        summary = panelUrl.ifEmpty { currentPanelName },
                        onClick = {
                            if (panelUrl.isBlank()) return@BasicComponent
                            when (panelOpenMode) {
                                LinkOpenMode.IN_APP -> onOpenInAppUrl(panelUrl)
                                LinkOpenMode.EXTERNAL_BROWSER -> onOpenExternalUrl(panelUrl)
                            }
                        },
                    )

                    WindowDropdownPreference(
                        title = MLang.ProfilesPage.LinkSettings.OpenMode,
                        summary =
                            panelOpenModeItems.getOrElse(panelOpenModeIndex) {
                                panelOpenModeItems.first()
                            },
                        items = panelOpenModeItems,
                        selectedIndex = panelOpenModeIndex,
                        onSelectedIndexChange = { index ->
                            viewModel.setPanelOpenMode(
                                when (index) {
                                    0 -> LinkOpenMode.IN_APP
                                    1 -> LinkOpenMode.EXTERNAL_BROWSER
                                    else -> LinkOpenMode.IN_APP
                                }
                            )
                        },
                    )
                }
            }

            item {
                Title(MLang.Feature.SubStore.SectionHint)
                Card {
                    ArrowPreference(
                        title =
                            if (isExtensionInstalled) {
                                MLang.Feature.SubStore.ExtensionInstalled
                            } else {
                                MLang.Feature.SubStore.ExtensionInstall
                            },
                        summary =
                            when {
                                isExtensionInstalled && isJavetLoaded ->
                                    MLang.Feature.SubStore.JavetAvailable
                                isExtensionInstalled -> MLang.Feature.SubStore.JavetPending
                                else -> MLang.Feature.SubStore.DownloadHint
                            },
                        onClick = {
                            if (!isExtensionInstalled) {
                                onOpenExternalUrl(
                                    "https://github.com/LM-Firefly/FlyCat/releases/tag/Expand"
                                )
                            } else {
                                viewModel.refreshExtensionStatus()
                            }
                        },
                    )
                    ArrowPreference(
                        title = MLang.Feature.SubStore.DownloadResources,
                        summary = MLang.Feature.SubStore.DownloadResourcesSummary,
                        onClick = { viewModel.downloadSubStoreAll() },
                        enabled = !isDownloadingSubStoreFrontend && !isDownloadingSubStoreBackend,
                    )
                }
            }
        }
    }
}

private fun panelUrlFor(panelType: Int): String {
    return when (panelType) {
        0 -> "https://board.zash.run.place"
        1 -> "https://metacubex.github.io/metacubexd"
        2 -> "https://yacd.haishan.me"
        else -> "https://board.zash.run.place"
    }
}
