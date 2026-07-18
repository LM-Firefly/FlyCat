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

@file:Suppress("FunctionName")

package com.github.lmfirefly.flycat.feature.settings.presentation.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.github.lmfirefly.flycat.core.model.WifiAutomationAction
import com.github.lmfirefly.flycat.core.model.WifiAutomationFallbackAction
import com.github.lmfirefly.flycat.core.model.WifiAutomationRule
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.WifiAutomationViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.*
import com.github.lmfirefly.flycat.presentation.component.dialog.*
import com.github.lmfirefly.flycat.presentation.component.misc.*
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.Wifi
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.runtime.api.wifi.WifiSsidNetwork
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class WifiPermissionAction { Enable, Scan }

/** Inline section of the network settings page. */
@Composable
fun WifiAutomationSettingsSection() {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel = koinViewModel<WifiAutomationViewModel>()
    val state by viewModel.uiState.collectAsState()
    var pendingAction by remember { mutableStateOf<WifiPermissionAction?>(null) }
    var scanSheetVisible by remember { mutableStateOf(false) }
    var editSheetVisible by remember { mutableStateOf(false) }
    var showPermissionExplanation by remember { mutableStateOf(false) }
    var showApproximateLocation by remember { mutableStateOf(false) }
    var showPermissionDenied by remember { mutableStateOf(false) }
    var showPermissionSettings by remember { mutableStateOf(false) }
    var showLocationSettings by remember { mutableStateOf(false) }

    fun completePendingAction() {
        when (pendingAction) {
            WifiPermissionAction.Enable -> viewModel.enable()
            WifiPermissionAction.Scan -> viewModel.scanWifi()
            null -> Unit
        }
        pendingAction = null
    }

    fun hasSsidPermission(): Boolean =
        when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.O -> true
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> hasPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) || hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

            else -> hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun systemLocationEnabled(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.O ||
            LocationManagerCompat.isLocationEnabled(
                context.getSystemService(android.location.LocationManager::class.java)
            )

    val locationSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (pendingAction != null && systemLocationEnabled()) {
                if (hasSsidPermission()) completePendingAction() else showPermissionExplanation = true
            }
        }
    val appSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (pendingAction != null && hasSsidPermission() && systemLocationEnabled()) {
                completePendingAction()
            }
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasSsidPermission()) {
                completePendingAction()
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {
                showApproximateLocation = true
            } else {
                val permission = requiredLocationPermissions().last()
                if (activity?.shouldShowRequestPermissionRationale(permission) == false) {
                    showPermissionSettings = true
                } else {
                    showPermissionDenied = true
                }
            }
        }

    fun requestSsidAccess(action: WifiPermissionAction) {
        pendingAction = action
        when {
            Build.VERSION.SDK_INT > Build.VERSION_CODES.O && !systemLocationEnabled() -> {
                showLocationSettings = true
            }

            hasSsidPermission() -> completePendingAction()
            state.locationRequested &&
                activity?.shouldShowRequestPermissionRationale(requiredLocationPermissions().last()) == false -> {
                showPermissionSettings = true
            }

            else -> showPermissionExplanation = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    WifiAutomationViewModel.Effect.AddedCurrentSsid -> FlyTxt.NetworkSettings.WifiAutomation.Added
                    WifiAutomationViewModel.Effect.SsidAlreadyExists -> FlyTxt.NetworkSettings.WifiAutomation.Duplicate
                    WifiAutomationViewModel.Effect.NoWifi -> FlyTxt.NetworkSettings.WifiAutomation.NoWifi
                    WifiAutomationViewModel.Effect.SsidUnavailable -> FlyTxt.NetworkSettings.WifiAutomation.Unavailable
                    WifiAutomationViewModel.Effect.ScanUnavailable -> FlyTxt.NetworkSettings.WifiAutomation.ScanUnavailable
                }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Title(FlyTxt.NetworkSettings.WifiAutomation.Title)
    Card {
        PreferenceSwitchItem(
            title = FlyTxt.NetworkSettings.WifiAutomation.EnabledTitle,
            checked = state.enabled,
            onCheckedChange = { enabled ->
                if (enabled) requestSsidAccess(WifiPermissionAction.Enable) else viewModel.disable()
            },
        )
    }
    Title(FlyTxt.NetworkSettings.WifiAutomation.WifiNameHeading)
    Card {
        PreferenceArrowItem(
            title = FlyTxt.NetworkSettings.WifiAutomation.ManualDialogTitle,
            onClick = {
                viewModel.resetScan()
                scanSheetVisible = true
                requestSsidAccess(WifiPermissionAction.Scan)
            },
        )
        PreferenceArrowItem(
            title = FlyTxt.NetworkSettings.WifiAutomation.EditDialogTitle,
            onClick = { editSheetVisible = true },
        )
    }
    Title(FlyTxt.NetworkSettings.WifiAutomation.NetworkChangeSection)
    Card {
        PreferenceEnumItem(
            title = FlyTxt.NetworkSettings.WifiAutomation.OtherWifiTitle,
            currentValue = state.otherWifiAction,
            items = fallbackActionLabels(),
            values = WifiAutomationFallbackAction.entries,
            onValueChange = viewModel::changeOtherWifiAction,
        )
        PreferenceEnumItem(
            title = FlyTxt.NetworkSettings.WifiAutomation.NoWifiTitle,
            currentValue = state.noWifiAction,
            items = fallbackActionLabels(),
            values = WifiAutomationFallbackAction.entries,
            onValueChange = viewModel::changeNoWifiAction,
        )
    }

    WifiScanSheet(
        show = scanSheetVisible,
        scannedNetworks = state.scannedNetworks,
        isScanning = state.isScanning,
        scanCompleted = state.scanCompleted,
        scanUnavailable = state.scanUnavailable,
        onDismiss = { scanSheetVisible = false },
        onDismissFinished = viewModel::resetScan,
        onConfirm = { ssid, action ->
            viewModel.addManualSsid(ssid, action)
            scanSheetVisible = false
        },
    )

    WifiRuleEditSheet(
        show = editSheetVisible,
        rules = state.rules,
        onDismiss = { editSheetVisible = false },
        onConfirm = { ssid, action ->
            viewModel.changeRuleAction(ssid, action)
            editSheetVisible = false
        },
        onDelete = viewModel::removeRule,
    )

    AppConfirmDialog(
        show = showPermissionExplanation,
        title = FlyTxt.NetworkSettings.WifiAutomation.PermissionTitle,
        message = FlyTxt.NetworkSettings.WifiAutomation.PermissionMessage,
        onDismissRequest = {
            pendingAction = null
            showPermissionExplanation = false
        },
        onConfirm = {
            showPermissionExplanation = false
            viewModel.markLocationPermissionRequested()
            permissionLauncher.launch(requiredLocationPermissions())
        },
        confirmText = FlyTxt.NetworkSettings.WifiAutomation.Grant,
    )
    AppConfirmDialog(
        show = showApproximateLocation,
        title = FlyTxt.NetworkSettings.WifiAutomation.ApproximateTitle,
        message = FlyTxt.NetworkSettings.WifiAutomation.ApproximateMessage,
        onDismissRequest = {
            pendingAction = null
            showApproximateLocation = false
        },
        onConfirm = {
            showApproximateLocation = false
            permissionLauncher.launch(requiredLocationPermissions())
        },
        confirmText = FlyTxt.NetworkSettings.WifiAutomation.Grant,
    )
    AppConfirmDialog(
        show = showPermissionDenied,
        title = FlyTxt.NetworkSettings.WifiAutomation.DeniedTitle,
        message = FlyTxt.NetworkSettings.WifiAutomation.DeniedMessage,
        onDismissRequest = {
            pendingAction = null
            showPermissionDenied = false
        },
        onConfirm = {
            showPermissionDenied = false
            showPermissionExplanation = true
        },
        confirmText = FlyTxt.NetworkSettings.WifiAutomation.Grant,
    )
    AppConfirmDialog(
        show = showPermissionSettings,
        title = FlyTxt.NetworkSettings.WifiAutomation.SettingsTitle,
        message = FlyTxt.NetworkSettings.WifiAutomation.SettingsMessage,
        onDismissRequest = {
            pendingAction = null
            showPermissionSettings = false
        },
        onConfirm = {
            showPermissionSettings = false
            appSettingsLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
        },
        confirmText = FlyTxt.NetworkSettings.WifiAutomation.OpenSettings,
    )
    AppConfirmDialog(
        show = showLocationSettings,
        title = FlyTxt.NetworkSettings.WifiAutomation.LocationTitle,
        message = FlyTxt.NetworkSettings.WifiAutomation.LocationMessage,
        onDismissRequest = {
            pendingAction = null
            showLocationSettings = false
        },
        onConfirm = {
            showLocationSettings = false
            locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        },
        confirmText = FlyTxt.NetworkSettings.WifiAutomation.TurnOnLocation,
    )
}

@Composable
private fun WifiScanSheet(
    show: Boolean,
    scannedNetworks: List<WifiSsidNetwork>,
    isScanning: Boolean,
    scanCompleted: Boolean,
    scanUnavailable: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: (ssid: String, action: WifiAutomationAction) -> Unit,
) {
    val spacing = AppTheme.spacing
    var selectedSsid by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf(WifiAutomationAction.Start) }

    LaunchedEffect(show) {
        if (show) {
            selectedSsid = null
            action = WifiAutomationAction.Start
        }
    }

    AppActionBottomSheet(
        show = show,
        title = FlyTxt.NetworkSettings.WifiAutomation.ManualDialogTitle,
        startAction = { AppBottomSheetCloseAction(onClick = onDismiss) },
        endAction = {
            AppBottomSheetConfirmAction(
                enabled = selectedSsid != null,
                onClick = { selectedSsid?.let { onConfirm(it, action) } },
            )
        },
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        insideMargin = DpSize(UiDp.dp12, UiDp.dp8),
        enableNestedScroll = true,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.space16),
        ) {
            when {
                isScanning -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 176.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            spacing.space12,
                            Alignment.CenterVertically,
                        ),
                    ) {
                        InfiniteProgressIndicator(modifier = Modifier.size(32.dp))
                        Text(
                            text = FlyTxt.NetworkSettings.WifiAutomation.Scanning,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }

                scanUnavailable -> BasicComponent(title = FlyTxt.NetworkSettings.WifiAutomation.ScanUnavailable)
                scanCompleted && scannedNetworks.isEmpty() ->
                    BasicComponent(title = FlyTxt.NetworkSettings.WifiAutomation.ScanEmpty)

                scannedNetworks.isNotEmpty() -> {
                    AnimatedVisibility(
                        visible = selectedSsid != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Card {
                            WindowDropdownPreference(
                                title = FlyTxt.NetworkSettings.WifiAutomation.ActionTitle,
                                items = listOf(
                                    FlyTxt.NetworkSettings.WifiAutomation.StartAction,
                                    FlyTxt.NetworkSettings.WifiAutomation.StopAction,
                                ),
                                selectedIndex = if (action == WifiAutomationAction.Start) 0 else 1,
                                onSelectedIndexChange = { index ->
                                    action =
                                        if (index == 0) {
                                            WifiAutomationAction.Start
                                        } else {
                                            WifiAutomationAction.Stop
                                        }
                                },
                            )
                        }
                    }
                    Card {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        ) {
                            items(scannedNetworks, key = { it.ssid }) { network ->
                                BasicComponent(
                                    title = network.ssid,
                                    summary = network.label,
                                    onClick = { selectedSsid = network.ssid },
                                    startAction = {
                                        Icon(
                                            modifier = Modifier.padding(end = UiDp.dp8),
                                            imageVector = FlyCat.Wifi,
                                            contentDescription = null,
                                        )
                                    },
                                    endActions = {
                                        RadioButton(
                                            selected = selectedSsid == network.ssid,
                                            onClick = { selectedSsid = network.ssid },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiRuleEditSheet(
    show: Boolean,
    rules: List<WifiAutomationRule>,
    onDismiss: () -> Unit,
    onConfirm: (ssid: String, action: WifiAutomationAction) -> Unit,
    onDelete: (ssid: String) -> Unit,
) {
    val spacing = AppTheme.spacing
    var selectedSsid by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf(WifiAutomationAction.Start) }
    val selectedRule = rules.firstOrNull { it.ssid == selectedSsid }

    LaunchedEffect(show) {
        if (show) selectedSsid = null
    }
    LaunchedEffect(selectedRule) {
        selectedRule?.let { action = it.action }
    }

    AppActionBottomSheet(
        show = show,
        title = FlyTxt.NetworkSettings.WifiAutomation.EditDialogTitle,
        startAction = { AppBottomSheetCloseAction(onClick = onDismiss) },
        endAction = {
            AppBottomSheetConfirmAction(
                enabled = selectedRule != null,
                onClick = { selectedRule?.let { onConfirm(it.ssid, action) } },
            )
        },
        onDismissRequest = onDismiss,
        insideMargin = DpSize(UiDp.dp12, UiDp.dp8),
        enableNestedScroll = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = UiDp.dp16),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            AnimatedVisibility(
                visible = selectedRule != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Card {
                    WindowDropdownPreference(
                        title = FlyTxt.NetworkSettings.WifiAutomation.ActionTitle,
                        items = listOf(
                            FlyTxt.NetworkSettings.WifiAutomation.StartAction,
                            FlyTxt.NetworkSettings.WifiAutomation.StopAction,
                        ),
                        selectedIndex = if (action == WifiAutomationAction.Start) 0 else 1,
                        onSelectedIndexChange = { index ->
                            action =
                                if (index == 0) WifiAutomationAction.Start else WifiAutomationAction.Stop
                        },
                    )
                }
            }
            if (rules.isEmpty()) {
                BasicComponent(title = FlyTxt.NetworkSettings.WifiAutomation.EmptyRules)
            } else {
                Card {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    ) {
                        items(rules, key = { it.ssid }) { rule ->
                            BasicComponent(
                                title = rule.ssid,
                                summary = actionLabel(rule.action),
                                onClick = { selectedSsid = rule.ssid },
                                startAction = {
                                    Icon(
                                        modifier = Modifier.padding(end = UiDp.dp8),
                                        imageVector = FlyCat.Wifi,
                                        contentDescription = null,
                                    )
                                },
                                endActions = {
                                    RadioButton(
                                        selected = selectedSsid == rule.ssid,
                                        onClick = { selectedSsid = rule.ssid },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = selectedRule != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                TextButton(
                    text = FlyTxt.Component.ProfileCard.Delete,
                    onClick = {
                        selectedRule?.let { onDelete(it.ssid) }
                        selectedSsid = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                )
            }
        }
    }
}

private fun actionLabel(action: WifiAutomationAction): String =
    when (action) {
        WifiAutomationAction.Start -> FlyTxt.NetworkSettings.WifiAutomation.StartAction
        WifiAutomationAction.Stop -> FlyTxt.NetworkSettings.WifiAutomation.StopAction
    }

@Composable
private fun fallbackActionLabels() =
    listOf(
        FlyTxt.NetworkSettings.WifiAutomation.KeepAction,
        FlyTxt.NetworkSettings.WifiAutomation.StartAction,
        FlyTxt.NetworkSettings.WifiAutomation.StopAction,
    )

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun requiredLocationPermissions(): Array<String> =
    when {
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.O -> emptyArray()
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.R -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    }
