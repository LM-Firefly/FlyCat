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

package com.github.lmfirefly.flycat.feature.substore.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.model.RemoteProtocol
import com.github.lmfirefly.flycat.feature.substore.model.AutoCloseMode
import com.github.lmfirefly.flycat.feature.substore.presentation.viewmodel.FeatureViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.input.EnumSelector
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.misc.Title
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.ui.platform.DeviceUtils
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
fun FeatureContent(
    onNavigateBack: () -> Unit,
    onOpenInAppUrl: (String) -> Unit,
    onOpenPanel: (url: String) -> Unit,
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
    val isDownloadingJavet by viewModel.isDownloadingJavet.collectAsStateWithLifecycle()
    val isJavetLoaded by viewModel.isJavetLoaded.collectAsStateWithLifecycle()
    val isSubStoreInitialized by viewModel.isSubStoreInitialized.collectAsStateWithLifecycle()
    val selectedPanelType by viewModel.selectedPanelType.state.collectAsStateWithLifecycle()
    val panelProtocol by viewModel.panelProtocol.state.collectAsStateWithLifecycle()

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
                val panelUrl = panelUrlFor(selectedPanelType, panelProtocol)

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
                    WindowDropdownPreference(
                        title = FlyTxt.Feature.Panel.Protocol,
                        summary = null,
                        items = RemoteProtocol.entries.map { it.scheme.uppercase() },
                        selectedIndex = RemoteProtocol.entries.indexOf(panelProtocol),
                        onSelectedIndexChange = { viewModel.setPanelProtocol(RemoteProtocol.entries[it]) },
                    )
                    ArrowPreference(
                        title = FlyTxt.Feature.Panel.OpenPanel,
                        summary = null,
                        enabled = panelUrl.isNotBlank(),
                        onClick = { onOpenPanel(panelUrl) },
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
                val canStartService = isJavetLoaded && isSubStoreInitialized
                val serviceStatusSummary = when {
                    isServiceRunning -> FlyTxt.Feature.ServiceStatus.Running.format(frontendUrl)
                    !isJavetLoaded -> FlyTxt.Feature.ServiceStatus.NeedExtension
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
                    if (!isJavetLoaded) {
                        ArrowPreference(
                            title = FlyTxt.Feature.SubStore.DownloadJavet,
                            summary = if (isDownloadingJavet) FlyTxt.Feature.SubStore.Downloading else FlyTxt.Feature.SubStore.DownloadHint,
                            enabled = !isDownloadingJavet,
                            onClick = { viewModel.downloadJavetLibrary() },
                        )
                    } else {
                        ArrowPreference(
                            title = FlyTxt.Feature.SubStore.JavetAvailable,
                            summary = null,
                            onClick = { viewModel.refreshExtensionStatus() },
                        )
                    }
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

private fun panelUrlFor(
    panelType: Int,
    protocol: RemoteProtocol,
): String {
    val host =
        when (panelType) {
            0 -> "board.zash.run.place"
            1 -> "metacubex.github.io/metacubexd"
            2 -> "yacd.haishan.me"
            else -> "board.zash.run.place"
        }
    return "${protocol.scheme}://$host"
}
