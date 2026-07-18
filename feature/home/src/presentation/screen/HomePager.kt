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

package com.github.lmfirefly.flycat.feature.home.presentation.screen

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
import com.github.lmfirefly.flycat.core.contract.AppSettingsReader
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.traffic.TrafficData
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunnelState.Mode
import com.github.lmfirefly.flycat.feature.home.presentation.viewmodel.HomeProxyControlState
import com.github.lmfirefly.flycat.feature.home.presentation.viewmodel.HomeViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalDetailNavigator
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalNavigator
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.navigation.Route
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.toast
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomePager(mainInnerPadding: PaddingValues, onOpenDashboard: (() -> Unit)? = null, isActive: Boolean) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val appSettings = koinInject<AppSettingsReader>()
    val navigator = LocalNavigator.current
    val detailNavigator = LocalDetailNavigator.current
    val openSecondary: (Route) -> Unit = { route -> if (detailNavigator != null) { detailNavigator.replaceAll(listOf(route)) } else { navigator.push(route) } }
    val controlState by homeViewModel.controlState.collectAsStateWithLifecycle()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val homeProfileState by homeViewModel.homeProfileState.collectAsStateWithLifecycle()
    val ipMonitoringState by homeViewModel.ipMonitoringState.collectAsStateWithLifecycle()
    val tunnelMode by homeViewModel.tunnelMode.collectAsStateWithLifecycle()
    val runMode by homeViewModel.runMode.collectAsStateWithLifecycle()
    val isRemoteController by homeViewModel.isRemoteController.collectAsStateWithLifecycle()
    val controllerBackendName by homeViewModel.controllerBackendName.collectAsStateWithLifecycle()
    val homeQuote by appSettings.moeHomeQuote.state.collectAsStateWithLifecycle()
    val homeQuoteAuthor by appSettings.moeHomeQuoteAuthor.state.collectAsStateWithLifecycle()
    val homeHitokotoEnabled by appSettings.homeHitokotoEnabled.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { homeViewModel.refreshRunMode() }

    LaunchedEffect(isActive) { homeViewModel.setHomeScreenActive(isActive) }

    DisposableEffect(homeViewModel) { onDispose { homeViewModel.setHomeScreenActive(false) } }

    DisposableEffect(lifecycleOwner, homeViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.reconcileRuntimeState()
                homeViewModel.refreshRunMode()
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
            homeProfileState.profilesLoaded && homeProfileState.profiles.isNotEmpty() && controlState.canInteract
        }

    val quoteTitle = if (homeHitokotoEnabled) homeQuote.ifBlank { FlyTxt.Home.Title } else FlyTxt.Home.Title
    Scaffold(topBar = { TopBar(title = quoteTitle, scrollBehavior = scrollBehavior, bottomContent = { if (homeHitokotoEnabled && homeQuote.isNotBlank() && homeQuoteAuthor.isNotBlank()) { Text(text = "—— ${homeQuoteAuthor}", style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.fillMaxWidth().padding(end = UiDp.dp24, bottom = UiDp.dp8), textAlign = TextAlign.End) } }) }) { innerPadding ->
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
        ) {
            item {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = UiDp.dp24),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement =
                        Arrangement.spacedBy(UiDp.dp24),
                ) {
                    HomeTrafficSection(
                        homeViewModel = homeViewModel,
                        isRunning = isRunning,
                        profileName = if (isRemoteController) { controllerBackendName } else { homeProfileState.currentProfile?.name?.takeIf { isRunning } },
                        tunnelMode = tunnelMode?.takeIf { isRunning },
                        currentProfileId = homeProfileState.currentProfile?.uuid?.toString(),
                        profileOptions = homeProfileState.profiles,
                        controlState = controlState,
                        runMode = runMode,
                        isRemoteController = isRemoteController,
                        isEnabled = isProxyEnabled,
                        onClick = {
                            if (isRemoteController) return@HomeTrafficSection
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            if (isRunning) {
                                coroutineScope.launch { homeViewModel.stopProxy() }
                                return@HomeTrafficSection
                            }
                            val targetProfile = homeProfileState.recommendedProfile
                            if (!homeProfileState.hasEnabledProfile || targetProfile == null) {
                                context.toast(FlyTxt.ProfilesVM.Error.ProfileNotExist)
                                return@HomeTrafficSection
                            }
                            homeViewModel.startProxy(profileId = targetProfile.uuid.toString(), mode = null)
                        },
                        onProfileNameClick = { hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey) },
                        onProfileSelected = { profileId -> hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey); homeViewModel.switchActiveProfile(profileId) },
                        onTunnelModeClick = { hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey) },
                        onTunnelModeSelected = { mode -> hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey); homeViewModel.switchTunnelMode(mode) },
                        onRunModeClick = { hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey) },
                        onRunModeSelected = { mode -> hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey); homeViewModel.switchRunMode(mode) },
                        onOpenDashboard = onOpenDashboard,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(UiDp.dp16)) {
                        HomeNodeInfoSection(homeViewModel = homeViewModel, isRunning = isRunning)
                        IpInfoDisplay(
                            state =
                                if (isRunning) {
                                    ipMonitoringState
                                } else {
                                    com.github.lmfirefly.flycat.core.model.IpMonitoringState
                                        .Loading
                                }
                        )
                        HomeSpeedChartSection(homeViewModel = homeViewModel, isRunning = isRunning, onClick = { openSecondary(Route.TrafficStatistics) }, isActive = isActive)
                        if (isRunning) { HomeTopologyChartSection(homeViewModel = homeViewModel, onClick = { openSecondary(Route.Connection) }) }
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
private fun HomeTrafficSection(homeViewModel: HomeViewModel, isRunning: Boolean, profileName: String?, tunnelMode:Mode?, currentProfileId: String?, profileOptions: List<Profile>, controlState: HomeProxyControlState, runMode: RunMode, isRemoteController: Boolean, isEnabled: Boolean, onClick: () -> Unit, onProfileNameClick: () -> Unit, onProfileSelected: (String) -> Unit, onTunnelModeClick: () -> Unit, onTunnelModeSelected: (Mode) -> Unit, onRunModeClick: () -> Unit, onRunModeSelected: (RunMode) -> Unit, onOpenDashboard: (() -> Unit)? = null) {
    val trafficData by homeViewModel.trafficData.collectAsStateWithLifecycle()
    TrafficDisplay(trafficNow = if (isRunning) trafficData else TrafficData.zero, profileName = profileName, tunnelMode = tunnelMode, currentProfileId = currentProfileId, profileOptions = profileOptions, controlState = controlState, runMode = runMode, isRemoteController = isRemoteController, isEnabled = isEnabled, onClick = onClick, onProfileNameClick = onProfileNameClick, onProfileSelected = onProfileSelected, onTunnelModeClick = onTunnelModeClick, onTunnelModeSelected = onTunnelModeSelected, onRunModeClick = onRunModeClick, onRunModeSelected = onRunModeSelected, onOpenDashboard = onOpenDashboard)
}

/**
 * Wrapper composable that collects [HomeViewModel.speedHistory] (1Hz) internally.
 */
@Composable
private fun HomeSpeedChartSection(homeViewModel: HomeViewModel, isRunning: Boolean, onClick: () -> Unit, isActive: Boolean = true) {
    val speedHistory by homeViewModel.speedHistory.collectAsStateWithLifecycle()
    SpeedChart(speedHistory = speedHistory, isRunning = isRunning, onClick = onClick, isActive = isActive)
}

/**
 * Wrapper composable that collects [HomeViewModel.connections] (1Hz) internally.
 */
@Composable
private fun HomeTopologyChartSection(homeViewModel: HomeViewModel, onClick: () -> Unit) {
    val connections by homeViewModel.connections.collectAsStateWithLifecycle()
    TopologyChart(connections = connections, onClick = onClick)
}

/**
 * Wrapper composable that collects [HomeViewModel.selectedServerName] and [HomeViewModel.selectedServerPing] (1Hz) internally,
 * isolating their recomposition from the parent [HomePager].
 */
@Composable
private fun HomeNodeInfoSection(homeViewModel: HomeViewModel, isRunning: Boolean) {
    val selectedServerName by homeViewModel.selectedServerName.collectAsStateWithLifecycle()
    val selectedServerPing by homeViewModel.selectedServerPing.collectAsStateWithLifecycle()
    NodeInfoDisplay(serverName = selectedServerName.takeIf { isRunning }, serverPing = selectedServerPing.takeIf { isRunning })
}
