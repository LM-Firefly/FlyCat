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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.traffic.TrafficData
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.model.tunnel.TunnelState
import com.github.lmfirefly.flycat.core.util.format.formatBytesForDisplay
import com.github.lmfirefly.flycat.feature.home.presentation.viewmodel.HomeProxyControlState
import com.github.lmfirefly.flycat.feature.home.presentation.viewmodel.HomeViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassCapsuleSurface
import com.github.lmfirefly.flycat.presentation.component.GlassMenuItem
import com.github.lmfirefly.flycat.presentation.component.GlassPopupHost
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.Activity
import com.github.lmfirefly.flycat.presentation.icon.flycat.PlaneTakeoff
import com.github.lmfirefly.flycat.presentation.icon.flycat.Rocket
import com.github.lmfirefly.flycat.presentation.icon.flycat.Tun
import com.github.lmfirefly.flycat.presentation.icon.flycat.Waiting
import com.github.lmfirefly.flycat.presentation.icon.flycat.Wifi
import com.github.lmfirefly.flycat.presentation.icon.flycat.Zashboard
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TrafficDisplay(
    trafficNow: TrafficData,
    profileName: String?,
    tunnelMode: TunnelState.Mode?,
    currentProfileId: String?,
    profileOptions: List<Profile>,
    controlState: HomeProxyControlState,
    runMode: RunMode,
    isRemoteController: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    onProfileNameClick: () -> Unit = {},
    onProfileSelected: (String) -> Unit = {},
    onTunnelModeClick: () -> Unit = {},
    onTunnelModeSelected: (TunnelState.Mode) -> Unit = {},
    onRunModeClick: () -> Unit = {},
    onRunModeSelected: (RunMode) -> Unit = {},
    onOpenDashboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    Column(
        modifier = modifier.fillMaxWidth().padding(top = componentSizes.homeTrafficTopPadding, bottom = spacing.space16),
        verticalArrangement = Arrangement.spacedBy(spacing.space24),
    ) {
        DownloadSection(
            downloadSpeed = trafficNow.download,
            profileName = profileName,
            tunnelMode = tunnelMode,
            currentProfileId = currentProfileId,
            profileOptions = profileOptions,
            onProfileNameClick = onProfileNameClick,
            onProfileSelected = onProfileSelected,
            onTunnelModeClick = onTunnelModeClick,
            onTunnelModeSelected = onTunnelModeSelected,
        )

        UploadSection(
            uploadSpeed = trafficNow.upload,
            controlState = controlState,
            runMode = runMode,
            isEnabled = isEnabled,
            isRemoteController = isRemoteController,
            onClick = onClick,
            onRunModeClick = onRunModeClick,
            onRunModeSelected = onRunModeSelected,
            onOpenDashboard = onOpenDashboard,
        )
    }
}

@Composable
private fun DownloadSection(
    downloadSpeed: Long,
    profileName: String?,
    tunnelMode: TunnelState.Mode?,
    currentProfileId: String?,
    profileOptions: List<Profile>,
    onProfileNameClick: () -> Unit = {},
    onProfileSelected: (String) -> Unit = {},
    onTunnelModeClick: () -> Unit = {},
    onTunnelModeSelected: (TunnelState.Mode) -> Unit = {},
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes

    Column(horizontalAlignment = Alignment.Start) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = componentSizes.statusCapsuleHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = FlyTxt.Home.Traffic.DownloadLong,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 14.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            ProfileModeBadge(profileName = profileName, tunnelMode = tunnelMode, currentProfileId = currentProfileId, profileOptions = profileOptions, onProfileNameClick = onProfileNameClick, onProfileSelected = onProfileSelected, onTunnelModeClick = onTunnelModeClick, onTunnelModeSelected = onTunnelModeSelected)
        }

        SpeedValue(speed = downloadSpeed)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileModeBadge(profileName: String?, tunnelMode: TunnelState.Mode?, currentProfileId: String?, profileOptions: List<Profile>, onProfileNameClick: () -> Unit = {}, onProfileSelected: (String) -> Unit = {}, onTunnelModeClick: () -> Unit = {}, onTunnelModeSelected: (TunnelState.Mode) -> Unit = {}) {
    if (profileName == null && tunnelMode == null) return
    val spacing = AppTheme.spacing
    val opacity = AppTheme.opacity
    val enabledProfiles = remember(profileOptions) { profileOptions }
    var showProfilePopup by rememberSaveable { mutableStateOf(false) }
    var showModePopup by rememberSaveable { mutableStateOf(false) }
    val modeOptions = remember { listOf(TunnelState.Mode.Rule, TunnelState.Mode.Global, TunnelState.Mode.Direct) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
        val profileMenuAnchor = rememberGlassPopupAnchor()
        val profileChipContent: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(spacing.space6)) {
                Text(text = profileName ?: FlyTxt.Home.Traffic.NoProfile, style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = MiuixTheme.colorScheme.primary, modifier = Modifier.weight(1f, fill = false).basicMarquee(iterations = Int.MAX_VALUE, velocity = 30.dp), maxLines = 1, softWrap = false)
            }
        }
        GlassCapsuleSurface(anchor = profileMenuAnchor, cornerRadius = 14.dp, fallbackColor = MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtle), modifier = Modifier.heightIn(min = 28.dp).widthIn(max = 200.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = { onProfileNameClick(); if (enabledProfiles.isNotEmpty()) { showProfilePopup = true } }), content = profileChipContent)
        GlassPopupHost(
            show = showProfilePopup,
            onDismissRequest = { showProfilePopup = false },
            anchor = profileMenuAnchor,
            anchorContent = profileChipContent,
            alignToPageEnd = false,
            sizing = GlassPopupSizing(minWidth = 188.dp, maxWidth = 280.dp),
        ) {
            enabledProfiles.forEach { profile ->
                GlassMenuItem(
                    text = profile.name,
                    selected = currentProfileId == profile.uuid.toString(),
                    onClick = { showProfilePopup = false; onProfileSelected(profile.uuid.toString()) },
                    textMaxLines = Int.MAX_VALUE,
                )
            }
        }
        Box(modifier = Modifier.size(spacing.space4).background(MiuixTheme.colorScheme.primary, CircleShape))
        val modeMenuAnchor = rememberGlassPopupAnchor()
        val modeChipContent: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(spacing.space6)) {
                Text(text = tunnelMode.toDisplayName(), style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = MiuixTheme.colorScheme.primary)
            }
        }
        GlassCapsuleSurface(anchor = modeMenuAnchor, cornerRadius = 14.dp, fallbackColor = MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtle), modifier = Modifier.heightIn(min = 28.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = { onTunnelModeClick(); showModePopup = true }), content = modeChipContent)
        GlassPopupHost(
            show = showModePopup,
            onDismissRequest = { showModePopup = false },
            anchor = modeMenuAnchor,
            anchorContent = modeChipContent,
            alignToPageEnd = false,
        ) {
            modeOptions.forEach { mode ->
                GlassMenuItem(text = mode.toDisplayName(), selected = mode == tunnelMode, onClick = { showModePopup = false; onTunnelModeSelected(mode) })
            }
        }
    }
}

@Composable
private fun SpeedValue(speed: Long) {
    val spacing = AppTheme.spacing
    val opacity = AppTheme.opacity

    val (value, unit) = formatBytesForDisplay(speed)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MiuixTheme.textStyles.headline1.copy(
                fontSize = trafficValueFontSize,
                lineHeight = trafficValueFontSize,
                letterSpacing = trafficValueLetterSpacing,
            ),
            color = MiuixTheme.colorScheme.primary,
        )
        Text(
            text = unit,
            style = MiuixTheme.textStyles.title2.copy(fontSize = trafficUnitFontSize),
            color = MiuixTheme.colorScheme.primary.copy(alpha = opacity.medium),
            modifier = Modifier.padding(bottom = spacing.space14, start = spacing.space8),
        )
    }
}

private val trafficValueFontSize = 96.sp
private val trafficValueLetterSpacing = (-3).sp
private val trafficUnitFontSize = 24.sp

@Composable
private fun UploadSection(
    uploadSpeed: Long,
    controlState: HomeProxyControlState,
    runMode: RunMode,
    isEnabled: Boolean,
    isRemoteController: Boolean,
    onClick: () -> Unit,
    onRunModeClick: () -> Unit = {},
    onRunModeSelected: (RunMode) -> Unit = {},
    onOpenDashboard: (() -> Unit)? = null,
) {
    val spacing = AppTheme.spacing

    val (value, unit) = formatBytesForDisplay(uploadSpeed)
    val isRunning = controlState == HomeProxyControlState.Running
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            Text(
                text = FlyTxt.Home.Traffic.UploadLong,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 14.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = "$value $unit",
                style = MiuixTheme.textStyles.title2.copy(fontSize = 20.sp),
                color = MiuixTheme.colorScheme.primary,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.animateContentSize(tween(AnimationSpecs.DURATION_FAST, easing = AnimationSpecs.EmphasizedDecelerate)),
        ) {
            ProxyStatusCapsule(controlState = controlState, isEnabled = isEnabled, isRemoteController = isRemoteController, onClick = onClick)
            AnimatedVisibility(
                visible = isRunning,
                enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(AnimationSpecs.DURATION_MEDIUM, easing = AnimationSpecs.EmphasizedDecelerate),
                ) + fadeIn(tween(AnimationSpecs.DURATION_MEDIUM, easing = AnimationSpecs.EnterEasing)),
                exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(AnimationSpecs.DURATION_FAST, easing = AnimationSpecs.EmphasizedAccelerate),
                ) + fadeOut(tween(AnimationSpecs.DURATION_FAST, easing = AnimationSpecs.ExitEasing)),
            ) { ProxyTypeCapsule(runMode = runMode, isEnabled = isEnabled, onRunModeClick = onRunModeClick, onRunModeSelected = onRunModeSelected) }
            if (isRunning && onOpenDashboard != null) { NetworkPanelStatusButton(onClick = onOpenDashboard) }
        }
    }
}

@Composable
private fun ProxyTypeCapsule(runMode: RunMode, isEnabled: Boolean, onRunModeClick: () -> Unit = {}, onRunModeSelected: (RunMode) -> Unit = {}) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val opacity = AppTheme.opacity
    val primary = MiuixTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    var showPopup by rememberSaveable { mutableStateOf(false) }
    val runModeOptions = remember { listOf(RunMode.VpnService, RunMode.Tun, RunMode.Ebpf) }
    val runModeMenuAnchor = rememberGlassPopupAnchor()
    val runModeChipContent: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = spacing.space12), horizontalArrangement = Arrangement.spacedBy(spacing.space6)) {
            Icon(imageVector = when (runMode) { RunMode.VpnService -> FlyCat.PlaneTakeoff; RunMode.Tun -> FlyCat.Tun; RunMode.Ebpf -> FlyCat.Tun }, contentDescription = null, tint = primary, modifier = Modifier.size(spacing.space12))
            Text(text = when (runMode) { RunMode.VpnService -> FlyTxt.Home.RunMode.Vpn; RunMode.Tun -> FlyTxt.Home.RunMode.Tun; RunMode.Ebpf -> FlyTxt.Home.RunMode.Ebpf }, style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = primary)
        }
    }
    GlassCapsuleSurface(anchor = runModeMenuAnchor, cornerRadius = componentSizes.statusCapsuleHeight / 2, fallbackColor = primary.copy(alpha = opacity.subtle), modifier = Modifier.height(componentSizes.statusCapsuleHeight).clickable(enabled = isEnabled, interactionSource = interactionSource, indication = null, role = Role.Button, onClick = { onRunModeClick(); showPopup = true }), content = runModeChipContent)
    GlassPopupHost(
        show = showPopup,
        onDismissRequest = { showPopup = false },
        anchor = runModeMenuAnchor,
        anchorContent = runModeChipContent,
        alignToPageEnd = false,
    ) {
        runModeOptions.forEach { mode ->
            GlassMenuItem(
                text = when (mode) { RunMode.VpnService -> FlyTxt.Home.RunMode.Vpn; RunMode.Tun -> FlyTxt.Home.RunMode.Tun; RunMode.Ebpf -> FlyTxt.Home.RunMode.Ebpf },
                selected = mode == runMode,
                onClick = { showPopup = false; onRunModeSelected(mode) },
            )
        }
    }
}

@Composable
private fun NetworkPanelStatusButton(onClick: () -> Unit) {
    val componentSizes = AppTheme.sizes
    val primary = MiuixTheme.colorScheme.primary
    GlassCapsuleSurface(
        cornerRadius = componentSizes.statusCapsuleHeight / 2,
        fallbackColor = primary.copy(alpha = AppTheme.opacity.subtle),
        modifier = Modifier.size(componentSizes.statusCapsuleHeight).clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = FlyCat.Zashboard,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(AppTheme.spacing.space16),
        )
    }
}

@Composable
private fun ProxyStatusCapsule(controlState: HomeProxyControlState, isEnabled: Boolean, isRemoteController: Boolean, onClick: () -> Unit) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val opacity = AppTheme.opacity

    val primary = MiuixTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    GlassCapsuleSurface(
        cornerRadius = componentSizes.statusCapsuleHeight / 2,
        fallbackColor = primary.copy(alpha = opacity.subtle),
        modifier = Modifier.height(componentSizes.statusCapsuleHeight).clickable(enabled = isEnabled, interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick).animateContentSize(
            tween(AnimationSpecs.DURATION_FAST, easing = AnimationSpecs.EmphasizedDecelerate)
        ),
    ) {
        AnimatedContent(
            targetState = controlState,
            transitionSpec = {
                (slideInHorizontally(
                    initialOffsetX = { it / 2 },
                    animationSpec = tween(AnimationSpecs.DURATION_MEDIUM, easing = AnimationSpecs.EmphasizedDecelerate),
                ) + fadeIn(tween(AnimationSpecs.DURATION_MEDIUM, easing = AnimationSpecs.EnterEasing))).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { -it / 2 },
                        animationSpec = tween(AnimationSpecs.DURATION_FAST, easing = AnimationSpecs.EmphasizedAccelerate),
                    ) + fadeOut(tween(AnimationSpecs.DURATION_FAST, easing = AnimationSpecs.ExitEasing))
                )
            },
            label = "CapsuleStateTransition",
        ) { state ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = spacing.space12),
                horizontalArrangement = Arrangement.spacedBy(spacing.space6),
            ) {
                Icon(
                    imageVector = when (state) {
                        HomeProxyControlState.Idle -> FlyCat.Rocket
                        HomeProxyControlState.Connecting,
                        HomeProxyControlState.Disconnecting -> FlyCat.Waiting
                        HomeProxyControlState.Lost,
                        HomeProxyControlState.Running -> FlyCat.Activity
                    },
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(spacing.space12),
                )
                Text(
                    text = when (state) {
                        HomeProxyControlState.Idle -> FlyTxt.Home.Status.TapToStart
                        HomeProxyControlState.Connecting -> FlyTxt.Home.Status.Connecting
                        HomeProxyControlState.Running -> FlyTxt.Home.Status.Running
                        HomeProxyControlState.Lost -> FlyTxt.Home.Status.Lost
                        HomeProxyControlState.Disconnecting -> FlyTxt.Home.Status.Disconnecting
                    },
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = primary,
                )
            }
        }
    }
}

private fun TunnelState.Mode?.toDisplayName(): String = when (this) {
    TunnelState.Mode.Direct -> FlyTxt.Home.TunnelMode.Direct
    TunnelState.Mode.Global -> FlyTxt.Home.TunnelMode.Global
    TunnelState.Mode.Rule -> FlyTxt.Home.TunnelMode.Rule
    else -> "--"
}
