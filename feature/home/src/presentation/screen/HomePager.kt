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

package com.github.yumelira.yumebox.feature.home.presentation.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.github.yumelira.yumebox.common.AppConstants
import com.github.yumelira.yumebox.core.data.AppSettingsReader
import com.github.yumelira.yumebox.core.model.TrafficData
import com.github.yumelira.yumebox.feature.home.presentation.viewmodel.HomeProxyControlState
import com.github.yumelira.yumebox.feature.home.presentation.viewmodel.HomeViewModel
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.theme.UiDp
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomePager(mainInnerPadding: PaddingValues, isActive: Boolean) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val appSettings = koinInject<AppSettingsReader>()
    val navigator = LocalNavigator.current

    val controlState by homeViewModel.controlState.collectAsStateWithLifecycle()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val profiles by homeViewModel.profiles.collectAsStateWithLifecycle()
    val profilesLoaded by homeViewModel.profilesLoaded.collectAsStateWithLifecycle()
    val ipMonitoringState by homeViewModel.ipMonitoringState.collectAsStateWithLifecycle()
    val recommendedProfile by homeViewModel.recommendedProfile.collectAsStateWithLifecycle()
    val hasEnabledProfile by homeViewModel.hasEnabledProfile.collectAsStateWithLifecycle(initialValue = false)
    val currentProfile by homeViewModel.currentProfile.collectAsStateWithLifecycle()
    val selectedServerName by homeViewModel.selectedServerName.collectAsStateWithLifecycle()
    val selectedServerPing by homeViewModel.selectedServerPing.collectAsStateWithLifecycle()
    val tunnelMode by homeViewModel.tunnelMode.collectAsStateWithLifecycle()
    val proxyMode by homeViewModel.proxyMode.collectAsStateWithLifecycle()
    val isRemoteController by homeViewModel.isRemoteController.collectAsStateWithLifecycle()
    val controllerBackendName by homeViewModel.controllerBackendName.collectAsStateWithLifecycle()
    val homeQuote by appSettings.moeHomeQuote.state.collectAsStateWithLifecycle()
    val homeQuoteAuthor by appSettings.moeHomeQuoteAuthor.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { homeViewModel.refreshProxyMode() }

    LaunchedEffect(isActive) { homeViewModel.setHomeScreenActive(isActive) }

    DisposableEffect(homeViewModel) { onDispose { homeViewModel.setHomeScreenActive(false) } }

    DisposableEffect(lifecycleOwner, homeViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.reconcileRuntimeState()
                homeViewModel.refreshProxyMode()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            context.toast(it, Toast.LENGTH_LONG)
            homeViewModel.consumeError()
        }
    }

    LaunchedEffect(uiState.message) { uiState.message?.let { homeViewModel.consumeMessage() } }

    val scrollBehavior = MiuixScrollBehavior()

    val isRunning = controlState == HomeProxyControlState.Running
    val isProxyEnabled =
        if (isRemoteController) {
            false
        } else {
            profilesLoaded && profiles.isNotEmpty() && controlState.canInteract
        }

    val quoteTitle = homeQuote.ifBlank { MLang.Home.Title }
    Scaffold(
        topBar = {
            TopBar(
                title = quoteTitle,
                scrollBehavior = scrollBehavior,
                bottomContent = {
                    if (homeQuote.isNotBlank() && homeQuoteAuthor.isNotBlank()) {
                        Text(
                            text = "—— ${homeQuoteAuthor}",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.fillMaxWidth().padding(end = UiDp.dp24, bottom = UiDp.dp8),
                            textAlign = TextAlign.End,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
        ) {
            item {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = AppConstants.UI.DEFAULT_HORIZONTAL_PADDING),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement =
                        Arrangement.spacedBy(AppConstants.UI.DEFAULT_VERTICAL_SPACING),
                ) {
                    HomeTrafficSection(
                        homeViewModel = homeViewModel,
                        isRunning = isRunning,
                        profileName = if (isRemoteController) {
                            controllerBackendName
                        } else {
                            currentProfile?.name?.takeIf { isRunning }
                        },
                        tunnelMode = tunnelMode?.takeIf { isRunning },
                        currentProfileId = currentProfile?.uuid?.toString(),
                        profileOptions = profiles,
                        controlState = controlState,
                        proxyMode = proxyMode,
                        isRemoteController = isRemoteController,
                        isEnabled = isProxyEnabled,
                        onClick = {
                            if (isRemoteController) return@HomeTrafficSection
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            if (isRunning) {
                                coroutineScope.launch { homeViewModel.stopProxy() }
                                return@HomeTrafficSection
                            }
                            val targetProfile = recommendedProfile
                            if (!hasEnabledProfile || targetProfile == null) {
                                context.toast(MLang.ProfilesVM.Error.ProfileNotExist)
                                return@HomeTrafficSection
                            }
                            homeViewModel.startProxy(
                                profileId = targetProfile.uuid.toString(),
                                mode = null,
                            )
                        },
                        onProfileNameClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        },
                        onProfileSelected = { profileId ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            homeViewModel.switchActiveProfile(profileId)
                        },
                        onTunnelModeClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        },
                        onTunnelModeSelected = { mode ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            homeViewModel.switchTunnelMode(mode)
                        },
                        onProxyModeClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        },
                        onProxyModeSelected = { mode ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            homeViewModel.switchProxyMode(mode)
                        },
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(UiDp.dp16)) {
                        NodeInfoDisplay(
                            serverName = selectedServerName.takeIf { isRunning },
                            serverPing = selectedServerPing.takeIf { isRunning },
                        )
                        IpInfoDisplay(
                            state =
                                if (isRunning) {
                                    ipMonitoringState
                                } else {
                                    com.github.yumelira.yumebox.core.model.IpMonitoringState
                                        .Loading
                                }
                        )
                        HomeSpeedChartSection(
                            homeViewModel = homeViewModel,
                            isRunning = isRunning,
                            onClick = { navigator.push(Route.TrafficStatistics) },
                        )
                        if (isRunning) {
                            HomeTopologyChartSection(
                                homeViewModel = homeViewModel,
                                onClick = { navigator.push(Route.Connection) },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(UiDp.dp32)) }
        }
    }
}

/**
 * Wrapper composable that collects [HomeViewModel.trafficData] (1Hz) internally,
 * isolating its recomposition from the parent [HomePager].
 */
@Composable
private fun HomeTrafficSection(
    homeViewModel: HomeViewModel,
    isRunning: Boolean,
    profileName: String?,
    tunnelMode: com.github.yumelira.yumebox.core.model.TunnelState.Mode?,
    currentProfileId: String?,
    profileOptions: List<com.github.yumelira.yumebox.core.model.Profile>,
    controlState: com.github.yumelira.yumebox.feature.home.presentation.viewmodel.HomeProxyControlState,
    proxyMode: com.github.yumelira.yumebox.core.model.ProxyMode,
    isRemoteController: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    onProfileNameClick: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onTunnelModeClick: () -> Unit,
    onTunnelModeSelected: (com.github.yumelira.yumebox.core.model.TunnelState.Mode) -> Unit,
    onProxyModeClick: () -> Unit,
    onProxyModeSelected: (com.github.yumelira.yumebox.core.model.ProxyMode) -> Unit,
) {
    val trafficData by homeViewModel.trafficData.collectAsStateWithLifecycle()
    TrafficDisplay(
        trafficNow = if (isRunning) trafficData else TrafficData.ZERO,
        profileName = profileName,
        tunnelMode = tunnelMode,
        currentProfileId = currentProfileId,
        profileOptions = profileOptions,
        controlState = controlState,
        proxyMode = proxyMode,
        isRemoteController = isRemoteController,
        isEnabled = isEnabled,
        onClick = onClick,
        onProfileNameClick = onProfileNameClick,
        onProfileSelected = onProfileSelected,
        onTunnelModeClick = onTunnelModeClick,
        onTunnelModeSelected = onTunnelModeSelected,
        onProxyModeClick = onProxyModeClick,
        onProxyModeSelected = onProxyModeSelected,
    )
}

/**
 * Wrapper composable that collects [HomeViewModel.speedHistory] (1Hz) internally.
 */
@Composable
private fun HomeSpeedChartSection(
    homeViewModel: HomeViewModel,
    isRunning: Boolean,
    onClick: () -> Unit,
) {
    val speedHistory by homeViewModel.speedHistory.collectAsStateWithLifecycle()
    SpeedChart(speedHistory = speedHistory, isRunning = isRunning, onClick = onClick)
}

/**
 * Wrapper composable that collects [HomeViewModel.connections] (1Hz) internally.
 */
@Composable
private fun HomeTopologyChartSection(
    homeViewModel: HomeViewModel,
    onClick: () -> Unit,
) {
    val connections by homeViewModel.connections.collectAsStateWithLifecycle()
    TopologyChart(connections = connections, onClick = onClick)
}
