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

package com.github.yumelira.yumebox.feature.home.presentation.screen.moe

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.core.contract.AppSettingsReader
import com.github.yumelira.yumebox.core.model.TrafficData
import com.github.yumelira.yumebox.core.model.ThemeMode
import com.github.yumelira.yumebox.core.util.AppForegroundState
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.feature.home.presentation.viewmodel.HomeProxyControlState
import com.github.yumelira.yumebox.feature.home.presentation.viewmodel.HomeViewModel
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.LocalHandlePageChange
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.icon.ShellIcons
import com.github.yumelira.yumebox.presentation.theme.AnimationSpecs
import com.github.yumelira.yumebox.presentation.navigation.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun MoeHomePage(
    mainInnerPadding: PaddingValues,
    wallpaperUri: String,
    wallpaperZoom: Float,
    wallpaperBiasX: Float,
    wallpaperBiasY: Float,
    isActive: Boolean,
    pageProgress: Float = 1f,
    sidebarProgress: Float = pageProgress,
) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val settings = koinInject<AppSettingsReader>()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val controlState by homeViewModel.controlState.collectAsStateWithLifecycle()
    val profiles by homeViewModel.profiles.collectAsStateWithLifecycle()
    val profilesLoaded by homeViewModel.profilesLoaded.collectAsStateWithLifecycle()
    val recommendedProfile by homeViewModel.recommendedProfile.collectAsStateWithLifecycle()
    val hasEnabledProfile by homeViewModel.hasEnabledProfile.collectAsStateWithLifecycle(initialValue = false)
    val selectedServerName by homeViewModel.selectedServerName.collectAsStateWithLifecycle()
    val selectedServerPing by homeViewModel.selectedServerPing.collectAsStateWithLifecycle()
    val trafficData by homeViewModel.trafficData.collectAsStateWithLifecycle()
    val runtimeSnapshot by homeViewModel.runtimeSnapshot.collectAsStateWithLifecycle()
    val isRemoteController by homeViewModel.isRemoteController.collectAsStateWithLifecycle()
    val themeMode by settings.themeMode.state.collectAsStateWithLifecycle()
    val classicHomeEnabled by settings.classicHomeEnabled.state.collectAsStateWithLifecycle()
    val homeHitokotoEnabled by settings.homeHitokotoEnabled.state.collectAsStateWithLifecycle()
    val moeHomeQuote by settings.moeHomeQuote.state.collectAsStateWithLifecycle()
    val moeHomeQuoteAuthor by settings.moeHomeQuoteAuthor.state.collectAsStateWithLifecycle()
    val sidebarExpanded by settings.moeSidebarExpanded.state.collectAsStateWithLifecycle()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val batteryPercent = rememberMoeBatteryPercent(context)
    var showHomeSettingsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { homeViewModel.refreshRunMode() }

    LaunchedEffect(isActive) {
        homeViewModel.setHomeScreenActive(isActive)
        if (isActive) {
            homeViewModel.reconcileRuntimeState()
            homeViewModel.refreshRunMode()
        }
    }

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

    val visualControlState = controlState
    // Tick once a second whether running or idle: running drives the elapsed timer, idle drives the
    // wall-clock shown in the rail so it always reflects the real time instead of a frozen 00:00.
    val now by
        produceState(initialValue = System.currentTimeMillis()) {
            snapshotFlow { AppForegroundState.foreground.value }
                .flatMapLatest { fg ->
                    if (fg) PollingTimers.ticks(PollingTimerSpecs.MoeElapsedClock) else emptyFlow()
                }
                .collect {
                    value = System.currentTimeMillis()
                }
        }
    val startedAt = runtimeSnapshot.startedAt
    val isRunning = visualControlState == HomeProxyControlState.Running
    val elapsedMillis =
        if (isRunning && startedAt != null && !isRemoteController) {
            (now - startedAt).coerceAtLeast(0L)
        } else {
            0L
        }
    val durationPair =
        remember(isRunning, isRemoteController, elapsedMillis, now) {
            if (isRunning && !isRemoteController) {
                formatMoeDuration(elapsedMillis)
            } else {
                formatMoeClock(now)
            }
        }
    val displayTrafficData =
        remember(trafficData, isRunning) {
            if (isRunning) trafficData else TrafficData.zero
        }
    val systemDark = isSystemInDarkTheme()
    val isDarkHomeSurface =
        when (themeMode) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
            ThemeMode.Auto -> systemDark
        }
    val contentSurface = if (isDarkHomeSurface) MiuixTheme.colorScheme.surface else Color.White
    val handlePageChange = LocalHandlePageChange.current
    val sidebarIcons = remember {
        listOf(
            MoeSidebarIconItem(ShellIcons.OpenProxy) { handlePageChange(1) },
            MoeSidebarIconItem(ShellIcons.OpenProfiles) { handlePageChange(2) },
            MoeSidebarIconItem(ShellIcons.OpenSettings) { handlePageChange(3) },
        )
    }
    val quote = remember(moeHomeQuote, moeHomeQuoteAuthor) { MoeQuote(text = moeHomeQuote.ifBlank { FlyTxt.AppSettings.Interface.HomeQuoteDefault }, author = moeHomeQuoteAuthor.ifBlank { FlyTxt.AppSettings.Interface.HomeQuoteAuthorDefault }) }
    val animatedSidebarToggleProgress by
        animateFloatAsState(
            targetValue = if (sidebarExpanded) 1f else 0f,
            animationSpec =
                tween(
                    durationMillis = if (sidebarExpanded) 420 else 320,
                    easing =
                        if (sidebarExpanded) {
                            AnimationSpecs.EmphasizedDecelerate
                        } else {
                            AnimationSpecs.EmphasizedAccelerate
                        },
                ),
            label = "moe_sidebar_toggle",
        )

    val handleProxyAction: () -> Unit =
        remember(isRemoteController, hasEnabledProfile, recommendedProfile, visualControlState) {
            {
                if (isRemoteController) {
                    // no-op
                } else if (!hasEnabledProfile || recommendedProfile == null) {
                    context.toast(FlyTxt.ProfilesVM.Error.ProfileNotExist, Toast.LENGTH_SHORT)
                } else if (visualControlState == HomeProxyControlState.Idle) {
                    recommendedProfile?.let { profile ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        scope.launch { homeViewModel.startProxy(profileId = profile.uuid.toString()) }
                    }
                } else if (visualControlState == HomeProxyControlState.Running) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    scope.launch { homeViewModel.stopProxy() }
                }
            }
        }

    val navigator = LocalNavigator.current
    val wallpaperPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            navigator.push(
                Route.MoeWallpaperCrop(
                    wallpaperUri = uri.toString(),
                    initialZoom = wallpaperZoom,
                    initialBiasX = wallpaperBiasX,
                    initialBiasY = wallpaperBiasY,
                )
            )
        }
    val launchWallpaperPicker = remember {
        {
            wallpaperPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    val layoutState = remember(wallpaperUri, wallpaperZoom, wallpaperBiasX, wallpaperBiasY, statusBarTop, pageProgress, sidebarProgress, animatedSidebarToggleProgress, durationPair, batteryPercent, contentSurface, isRunning, trafficData, selectedServerName, selectedServerPing, now, quote.text, quote.author, visualControlState, profilesLoaded, profiles, isRemoteController) {
        MoeHomeLayoutState(
            wallpaperUri = wallpaperUri,
            wallpaperZoom = wallpaperZoom,
            wallpaperBiasX = wallpaperBiasX,
            wallpaperBiasY = wallpaperBiasY,
            statusBarTop = statusBarTop,
            pageProgress = pageProgress,
            sidebarProgress = sidebarProgress,
            sidebarToggleProgress = animatedSidebarToggleProgress,
            duration = durationPair,
            batteryPercent = batteryPercent,
            sidebarIcons = sidebarIcons,
            contentSurface = contentSurface,
            isRunning = isRunning,
            traffic = trafficData,
            selectedServerName = selectedServerName,
            selectedServerPing = selectedServerPing,
            now = now,
            quote = quote.text,
            quoteAuthor = quote.author,
            controlState = visualControlState,
            canLaunch = profilesLoaded && profiles.isNotEmpty() && !isRemoteController,
            isRemoteController = isRemoteController,
        )
    }

    val actions = remember(handleProxyAction, sidebarExpanded) { MoeHomeActions(toggleSidebar = { settings.moeSidebarExpanded.set(!sidebarExpanded) }, pickWallpaper = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress); launchWallpaperPicker() }, openSettings = { showHomeSettingsSheet = true }, toggleProxy = handleProxyAction) }
    with(actions) { MoeHomeLayout(layoutState) }

    MoeHomeSettingsSheet(
        show = showHomeSettingsSheet,
        quote = moeHomeQuote,
        quoteAuthor = moeHomeQuoteAuthor,
        classicHomeEnabled = classicHomeEnabled,
        homeHitokotoEnabled = homeHitokotoEnabled,
        sidebarExpanded = sidebarExpanded,
        onQuoteChange = { settings.moeHomeQuote.set(it) },
        onQuoteAuthorChange = { settings.moeHomeQuoteAuthor.set(it) },
        onClassicHomeEnabledChange = { settings.classicHomeEnabled.set(it) },
        onHomeHitokotoEnabledChange = { settings.homeHitokotoEnabled.set(it) },
        onSidebarExpandedChange = { settings.moeSidebarExpanded.set(it) },
        onLaunchGalleryPicker = {
            showHomeSettingsSheet = false
            launchWallpaperPicker()
        },
        onNavigateToWallpaperCrop = { url ->
            showHomeSettingsSheet = false
            navigator.push(Route.MoeWallpaperCrop(wallpaperUri = url, initialZoom = wallpaperZoom, initialBiasX = wallpaperBiasX, initialBiasY = wallpaperBiasY))
        },
        onDismiss = { showHomeSettingsSheet = false },
    )
}

@Composable
private fun rememberMoeBatteryPercent(context: Context): Int? {
    val percent by
        produceState<Int?>(initialValue = null, context) {
            val batteryIntent =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            value =
                if (level >= 0 && scale > 0) {
                    ((level / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
                } else {
                    null
                }
        }
    return percent
}
