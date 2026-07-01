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

package com.github.yumelira.yumebox.screen.home

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.common.AppConstants
import com.github.yumelira.yumebox.core.domain.model.TrafficData
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.presentation.navigation.Route
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun HomePager(mainInnerPadding: PaddingValues, isActive: Boolean) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val navigator = LocalNavigator.current

    val controlState by homeViewModel.controlState.collectAsStateWithLifecycle()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val trafficData by homeViewModel.trafficData.collectAsStateWithLifecycle()
    val profiles by homeViewModel.profiles.collectAsStateWithLifecycle()
    val profilesLoaded by homeViewModel.profilesLoaded.collectAsStateWithLifecycle()
    val ipMonitoringState by homeViewModel.ipMonitoringState.collectAsStateWithLifecycle()
    val recommendedProfile by homeViewModel.recommendedProfile.collectAsStateWithLifecycle()
    val hasEnabledProfile by homeViewModel.hasEnabledProfile.collectAsStateWithLifecycle(initialValue = false)
    val currentProfile by homeViewModel.currentProfile.collectAsStateWithLifecycle()
    val selectedServerName by homeViewModel.selectedServerName.collectAsStateWithLifecycle()
    val selectedServerPing by homeViewModel.selectedServerPing.collectAsStateWithLifecycle()
    val speedHistory by homeViewModel.speedHistory.collectAsStateWithLifecycle()
    val connections by homeViewModel.connections.collectAsStateWithLifecycle()
    val tunnelMode by homeViewModel.tunnelMode.collectAsStateWithLifecycle()
    val proxyMode by homeViewModel.proxyMode.collectAsStateWithLifecycle()
    val isRemoteController by homeViewModel.isRemoteController.collectAsStateWithLifecycle()
    val controllerBackendName by homeViewModel.controllerBackendName.collectAsStateWithLifecycle()
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

    Scaffold(topBar = { TopBar(title = MLang.Home.Title, scrollBehavior = scrollBehavior) }) {
        innerPadding ->
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
                    TrafficDisplay(
                        trafficNow =
                            if (isRunning) {
                                trafficData
                             } else {
                                TrafficData.ZERO
                             },
                        // TODO(i18n M7): localize the "控制器" backend label.
                        profileName =
                            if (isRemoteController) {
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
                            if (isRemoteController) {
                                return@TrafficDisplay
                            }
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            if (isRunning) {
                                coroutineScope.launch { homeViewModel.stopProxy() }
                                return@TrafficDisplay
                            }
                            val targetProfile = recommendedProfile
                            if (!hasEnabledProfile || targetProfile == null) {
                                context.toast(MLang.ProfilesVM.Error.ProfileNotExist)
                                return@TrafficDisplay
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
                        SpeedChart(
                            speedHistory = speedHistory,
                            isRunning = isRunning,
                            onClick = {
                                navigator.push(Route.TrafficStatistics)
                            }
                        )
                        if (isRunning) {
                            TopologyChart(
                                connections = connections,
                                onClick = {
                                    navigator.push(Route.Connection)
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(UiDp.dp32)) }
        }
    }
}
