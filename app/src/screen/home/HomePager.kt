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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.domain.model.TrafficData
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.theme.UiDp
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun HomePager(mainInnerPadding: PaddingValues, isActive: Boolean) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val navigator = LocalNavigator.current

    val controlState by homeViewModel.controlState.collectAsState()
    val uiState by homeViewModel.uiState.collectAsState()
    val trafficNow by homeViewModel.trafficNow.collectAsState()
    val profiles by homeViewModel.profiles.collectAsState()
    val profilesLoaded by homeViewModel.profilesLoaded.collectAsState()
    val ipMonitoringState by homeViewModel.ipMonitoringState.collectAsState()
    val recommendedProfile by homeViewModel.recommendedProfile.collectAsState()
    val hasEnabledProfile by homeViewModel.hasEnabledProfile.collectAsState(initial = false)
    val currentProfile by homeViewModel.currentProfile.collectAsState()
    val selectedServerName by homeViewModel.selectedServerName.collectAsState()
    val selectedServerPing by homeViewModel.selectedServerPing.collectAsState()
    val speedHistory by homeViewModel.speedHistory.collectAsState()
    val proxyMode by homeViewModel.proxyMode.collectAsState()
    val isRemoteController by homeViewModel.isRemoteController.collectAsState()
    val controllerBackendName by homeViewModel.controllerBackendName.collectAsState()
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
                            .padding(horizontal = UiDp.dp24),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement =
                        Arrangement.spacedBy(UiDp.dp24),
                ) {
                    TrafficDisplay(
                        trafficNow =
                            if (isRunning) {
                                TrafficData.from(trafficNow)
                            } else {
                                TrafficData.zero
                            },
                        // TODO(i18n M7): localize the "控制器" backend label.
                        profileName =
                            if (isRemoteController) {
                                controllerBackendName
                            } else {
                                currentProfile?.name?.takeIf { isRunning }
                            },
                        tunnelMode = null,
                        controlState = controlState,
                        proxyMode = proxyMode,
                        isRemoteController = isRemoteController,
                        isEnabled = isProxyEnabled,
                        onClick = {
                            if (isRemoteController) {
                                return@TrafficDisplay
                            }
                            if (!hasEnabledProfile || recommendedProfile == null) {
                                context.toast(MLang.ProfilesVM.Error.ProfileNotExist)
                                return@TrafficDisplay
                            }
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            handleProxyToggle(
                                isRunning = isRunning,
                                recommendedProfile = recommendedProfile,
                                onStart = { profile ->
                                    homeViewModel.startProxy(
                                        profileId = profile.uuid.toString(),
                                        mode = null,
                                    )
                                },
                                onStop = { coroutineScope.launch { homeViewModel.stopProxy() } },
                            )
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
                                    com.github.yumelira.yumebox.data.gateway.IpMonitoringState
                                        .Loading
                                }
                        )
                    }

                    SpeedChart(
                        speedHistory = speedHistory,
                        isRunning = isRunning,
                        onClick = { navigator.push(Route.TrafficStatistics) },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(UiDp.dp32)) }
        }
    }
}

private fun handleProxyToggle(
    isRunning: Boolean,
    recommendedProfile: com.github.yumelira.yumebox.runtime.api.Profile?,
    onStart: (com.github.yumelira.yumebox.runtime.api.Profile) -> Unit,
    onStop: () -> Unit,
) {
    if (!isRunning) {
        recommendedProfile?.let { profile -> onStart(profile) }
    } else {
        onStop()
    }
}
