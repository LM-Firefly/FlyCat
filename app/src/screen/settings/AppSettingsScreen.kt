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

package com.github.yumelira.yumebox.screen.settings

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.yumelira.yumebox.common.util.AppIconHelper
import com.github.yumelira.yumebox.common.util.LocaleUtil
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.data.model.AppLanguage
import com.github.yumelira.yumebox.data.model.ThemeMode
import com.github.yumelira.yumebox.presentation.component.AppTextFieldDialog
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.PreferenceArrowItem
import com.github.yumelira.yumebox.presentation.component.PreferenceEnumItem
import com.github.yumelira.yumebox.presentation.component.PreferenceSwitchItem
import com.github.yumelira.yumebox.presentation.component.PreferenceValueItem
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.WarningBottomSheet
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.screen.settings.component.ThemeColorPickerItem
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppSettingsScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<AppSettingsViewModel>()

    Scaffold(
        topBar = { TopBar(title = MLang.AppSettings.Title, scrollBehavior = scrollBehavior) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item { AppBehaviorSettingsSection(viewModel) }
            item { AppInterfaceSettingsSection(viewModel = viewModel) }
            item { AppPrivacySettingsSection(viewModel) }
            item { AppServiceSettingsSection(viewModel) }
            item { AppNetworkSettingsSection(viewModel) }
        }
    }
}

@Composable
private fun AppBehaviorSettingsSection(viewModel: AppSettingsViewModel) {
    val automaticRestart by viewModel.automaticRestart.state.collectAsState()
    val autoUpdateCurrentProfileOnStart by
        viewModel.autoUpdateCurrentProfileOnStart.state.collectAsState()
    val isChineseLocale = remember { LocaleUtil.isChineseLocale() }

    Title(MLang.AppSettings.Section.Behavior)
    Card {
        PreferenceSwitchItem(
            title = MLang.AppSettings.Behavior.AutoStartTitle,
            checked = automaticRestart,
            onCheckedChange = viewModel::onAutomaticRestartChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.Behavior.AutoUpdateOnStartTitle,
            checked = autoUpdateCurrentProfileOnStart,
            onCheckedChange = viewModel::onAutoUpdateCurrentProfileOnStartChange,
        )
        if (isChineseLocale) {
            PreferenceSwitchItem(
                title = MLang.AppSettings.Behavior.OneChinaTitle,
                checked = true,
                onCheckedChange = {},
                enabled = false,
            )
        }
    }
}

@Composable
private fun AppInterfaceSettingsSection(viewModel: AppSettingsViewModel) {
    val themeMode by viewModel.themeMode.state.collectAsState()
    val appLanguage by viewModel.appLanguage.state.collectAsState()
    val themeSeedColorArgb by viewModel.themeSeedColorArgb.state.collectAsState()
    val invertOnPrimaryColors by viewModel.invertOnPrimaryColors.state.collectAsState()
    val bottomBarAutoHide by viewModel.bottomBarAutoHide.state.collectAsState()
    val topBarBlurEnabled by viewModel.topBarBlurEnabled.state.collectAsState()
    val pageScale by viewModel.pageScale.state.collectAsState()
    val classicHomeEnabled by viewModel.classicHomeEnabled.state.collectAsState()
    val homeQuote by viewModel.moeHomeQuote.state.collectAsState()
    val homeQuoteAuthor by viewModel.moeHomeQuoteAuthor.state.collectAsState()
    val homeQuoteSummary =
        remember(homeQuote) { homeQuote.ifBlank { MLang.AppSettings.Interface.HomeQuoteDefault } }
    val homeQuoteAuthorSummary =
        remember(homeQuoteAuthor) {
            homeQuoteAuthor.ifBlank { MLang.AppSettings.Interface.HomeQuoteAuthorDefault }
        }

    Title(MLang.AppSettings.Interface.ColorThemeTitle)
    Card {
        PreferenceEnumItem(
            title = MLang.AppSettings.Interface.ThemeModeTitle,
            currentValue = themeMode,
            items =
                listOf(
                    MLang.AppSettings.Interface.ThemeModeSystem,
                    MLang.AppSettings.Interface.ThemeModeLight,
                    MLang.AppSettings.Interface.ThemeModeDark,
                ),
            values = ThemeMode.entries,
            onValueChange = viewModel::onThemeModeChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.Interface.ThemeColorPolarityInvertTitle,
            checked = invertOnPrimaryColors,
            onCheckedChange = viewModel::onInvertOnPrimaryColorsChange,
        )
        ThemeColorPickerItem(
            themeSeedColorArgb = themeSeedColorArgb,
            onThemeSeedColorChange = viewModel::onThemeSeedColorChange,
        )
    }
    Title(MLang.AppSettings.Section.Interface)
    Card {
        PreferenceEnumItem(
            title = MLang.AppSettings.Interface.LanguageTitle,
            currentValue = appLanguage,
            items =
                listOf(
                    MLang.AppSettings.Interface.LanguageSystem,
                    MLang.AppSettings.Interface.LanguageChinese,
                    MLang.AppSettings.Interface.LanguageEnglish,
                ),
            values = AppLanguage.entries,
            onValueChange = viewModel::onAppLanguageChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.Interface.AutoHideNavbarTitle,
            checked = bottomBarAutoHide,
            onCheckedChange = viewModel::onBottomBarAutoHideChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.Interface.TopBarBlurTitle,
            checked = topBarBlurEnabled,
            onCheckedChange = viewModel::onTopBarBlurEnabledChange,
        )
        PageScalePreferenceItem(pageScale = pageScale, onApply = viewModel::onPageScaleChange)
    }
    Title(MLang.AppSettings.Section.Home)
    Card {
        PreferenceSwitchItem(
            title = MLang.AppSettings.Interface.ClassicHomeTitle,
            checked = classicHomeEnabled,
            onCheckedChange = viewModel::onClassicHomeEnabledChange,
        )
        MoeQuotePreferenceItem(
            title = MLang.AppSettings.Interface.HomeQuoteTitle,
            summary = homeQuoteSummary,
            dialogTitle = MLang.AppSettings.Interface.EditHomeQuoteTitle,
            currentValue = homeQuote,
            onConfirm = viewModel::onMoeHomeQuoteChange,
        )
        MoeQuotePreferenceItem(
            title = MLang.AppSettings.Interface.HomeQuoteAuthorTitle,
            summary = homeQuoteAuthorSummary,
            dialogTitle = MLang.AppSettings.Interface.EditHomeQuoteAuthorTitle,
            currentValue = homeQuoteAuthor,
            onConfirm = viewModel::onMoeHomeQuoteAuthorChange,
        )
    }
}

@Composable
private fun AppPrivacySettingsSection(viewModel: AppSettingsViewModel) {
    val context = LocalContext.current
    val excludeFromRecents by viewModel.excludeFromRecents.state.collectAsState()

    Title(MLang.AppSettings.Section.Privacy)
    Card {
        HideAppIconPreferenceItem(
            hideAppIconFlow = viewModel.hideAppIcon.state,
            onHideAppIconChange = viewModel::onHideAppIconChange,
            context = context,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.Privacy.HideFromRecentsTitle,
            checked = excludeFromRecents,
            onCheckedChange = viewModel::onExcludeFromRecentsChange,
        )
    }
}

@Composable
private fun AppServiceSettingsSection(viewModel: AppSettingsViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val showTrafficNotification by viewModel.showTrafficNotification.state.collectAsState()
    val singleNodeTest by viewModel.singleNodeTest.state.collectAsState()
    val exitUiWhenBackground by viewModel.exitUiWhenBackground.state.collectAsState()
    var batteryOptimizationIgnored by remember {
        mutableStateOf(isBatteryOptimizationIgnored(context))
    }
    val scope = rememberCoroutineScope()
    // Launch via activity-result so the summary refreshes as soon as the system grant dialog
    // returns — HyperOS shows it as a dialog-style activity where ON_RESUME timing is unreliable.
    val batteryOptimizationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            batteryOptimizationIgnored = isBatteryOptimizationIgnored(context)
            scope.launch {
                // The PowerManager whitelist state can lag slightly behind the dialog result.
                delay(500)
                batteryOptimizationIgnored = isBatteryOptimizationIgnored(context)
            }
        }
    val batteryOptimizationSummary =
        remember(batteryOptimizationIgnored) {
            if (batteryOptimizationIgnored) {
                MLang.AppSettings.ServiceSection.BatteryOptimizationSummaryEnabled
            } else {
                MLang.AppSettings.ServiceSection.BatteryOptimizationSummaryDisabled
            }
        }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimizationIgnored = isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Title(MLang.AppSettings.Section.Service)
    Card {
        PreferenceSwitchItem(
            title = MLang.AppSettings.ServiceSection.TrafficNotificationTitle,
            checked = showTrafficNotification,
            onCheckedChange = viewModel::onShowTrafficNotificationChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.ServiceSection.SingleNodeTestTitle,
            checked = singleNodeTest,
            onCheckedChange = viewModel::onSingleNodeTestChange,
        )
        PreferenceSwitchItem(
            title = MLang.AppSettings.ServiceSection.ExitUiWhenBackgroundTitle,
            checked = exitUiWhenBackground,
            onCheckedChange = viewModel::onExitUiWhenBackgroundChange,
        )
        PreferenceArrowItem(
            title = MLang.AppSettings.ServiceSection.BatteryOptimizationTitle,
            summary = batteryOptimizationSummary,
            onClick = {
                val launched =
                    batteryOptimizationIntents(context, batteryOptimizationIgnored).any { intent ->
                        runCatching { batteryOptimizationLauncher.launch(intent) }.isSuccess
                    }
                if (!launched) {
                    context.toast(MLang.Util.Error.UnknownError)
                }
            },
        )
    }
}

@Composable
private fun AppNetworkSettingsSection(viewModel: AppSettingsViewModel) {
    val customUserAgent by viewModel.customUserAgent.state.collectAsState()

    Title(MLang.AppSettings.Section.Network)
    Card {
        CustomUserAgentPreferenceItem(
            customUserAgent = customUserAgent,
            onConfirm = viewModel::applyCustomUserAgent,
        )
    }
}

@Composable
private fun HideAppIconPreferenceItem(
    hideAppIconFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    onHideAppIconChange: (Boolean) -> Unit,
    context: android.content.Context,
) {
    val hideAppIcon by hideAppIconFlow.collectAsState()
    val showHideIconDialogState = remember { mutableStateOf(false) }

    PreferenceSwitchItem(
        title = MLang.AppSettings.Privacy.HideIconTitle,
        checked = hideAppIcon,
        onCheckedChange = { checked ->
            if (checked) {
                showHideIconDialogState.value = true
            } else {
                onHideAppIconChange(false)
                AppIconHelper.toggleIcon(context, false)
            }
        },
    )

    WarningBottomSheet(
        show = showHideIconDialogState,
        title = MLang.AppSettings.WarningDialog.Title,
        messages =
            listOf(
                MLang.AppSettings.WarningDialog.HideIconMsg1,
                MLang.AppSettings.WarningDialog.HideIconMsg2,
            ),
        onConfirm = {
            onHideAppIconChange(true)
            AppIconHelper.toggleIcon(context, true)
        },
    )
}

@Composable
private fun MoeQuotePreferenceItem(
    title: String,
    summary: String,
    dialogTitle: String,
    currentValue: String,
    onConfirm: (String) -> Unit,
) {
    val showEditDialog = remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var localTextFieldValue by remember {
        mutableStateOf(
            TextFieldValue(text = currentValue, selection = TextRange(currentValue.length))
        )
    }

    PreferenceValueItem(
        title = title,
        summary = summary,
        onClick = {
            localTextFieldValue =
                TextFieldValue(text = currentValue, selection = TextRange(currentValue.length))
            showEditDialog.value = true
        },
    )

    AppTextFieldDialog(
        show = showEditDialog.value,
        title = dialogTitle,
        textFieldValue = localTextFieldValue,
        onTextFieldValueChange = { updatedTextFieldValue ->
            localTextFieldValue = updatedTextFieldValue
        },
        onDismissRequest = {
            showEditDialog.value = false
            focusManager.clearFocus()
        },
        onConfirm = {
            onConfirm(localTextFieldValue.text)
            focusManager.clearFocus()
            showEditDialog.value = false
        },
        singleLine = true,
        keyboardActions =
            KeyboardActions(
                onDone = {
                    onConfirm(localTextFieldValue.text)
                    focusManager.clearFocus()
                    showEditDialog.value = false
                }
            ),
    )
}

private fun isBatteryOptimizationIgnored(context: android.content.Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun batteryOptimizationIntents(
    context: android.content.Context,
    alreadyIgnored: Boolean,
): List<Intent> = buildList {
    if (!alreadyIgnored) {
        add(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
        )
    } else {
        // Already whitelisted: open this app's own power policy page instead of the global
        // "battery usage of all apps" list. MIUI/HyperOS per-app power keeper page first...
        add(
            Intent().apply {
                component =
                    ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                    )
                putExtra("package_name", context.packageName)
                putExtra(
                    "package_label",
                    context.applicationInfo.loadLabel(context.packageManager).toString(),
                )
            }
        )
    }
    // ...then the public per-app details page as the fallback for both branches.
    add(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    )
}

@Composable
private fun PageScalePreferenceItem(pageScale: Float, onApply: (Float) -> Unit) {
    var pageScaleLocal by remember(pageScale) { mutableFloatStateOf(pageScale) }
    val pageScalePercentText = remember(pageScaleLocal) { "${(pageScaleLocal * 100).toInt()}%" }
    val showPageScaleDialogState = remember { mutableStateOf(false) }

    PreferenceArrowItem(
        title = MLang.AppSettings.Interface.PageScaleTitle,
        endActions = {
            Text(
                text = pageScalePercentText,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = { showPageScaleDialogState.value = true },
        holdDownState = showPageScaleDialogState.value,
        bottomAction = {
            Slider(
                value = pageScaleLocal,
                onValueChange = { pageScaleLocal = it },
                onValueChangeFinished = { onApply(pageScaleLocal) },
                valueRange = 0.8f..1.2f,
                magnetThreshold = 0.01f,
                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            )
        },
    )

    PageScaleDialog(
        show = showPageScaleDialogState.value,
        pageScale = pageScaleLocal,
        onPageScaleChange = { pageScaleLocal = it },
        onApply = onApply,
        onDismissRequest = { showPageScaleDialogState.value = false },
    )
}

@Composable
private fun CustomUserAgentPreferenceItem(customUserAgent: String, onConfirm: (String) -> Unit) {
    val customUserAgentSummary =
        remember(customUserAgent) {
            customUserAgent.ifEmpty { MLang.AppSettings.Network.CustomUserAgentSummaryDefault }
        }
    val showEditCustomUserAgentDialog = remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var localTextFieldValue by remember {
        mutableStateOf(
            TextFieldValue(text = customUserAgent, selection = TextRange(customUserAgent.length))
        )
    }

    PreferenceArrowItem(
        title = MLang.AppSettings.Network.CustomUserAgentTitle,
        summary = customUserAgentSummary,
        onClick = {
            localTextFieldValue =
                TextFieldValue(
                    text = customUserAgent,
                    selection = TextRange(customUserAgent.length),
                )
            showEditCustomUserAgentDialog.value = true
        },
        holdDownState = showEditCustomUserAgentDialog.value,
    )

    AppTextFieldDialog(
        show = showEditCustomUserAgentDialog.value,
        title = MLang.AppSettings.EditDialog.UserAgentTitle,
        textFieldValue = localTextFieldValue,
        onTextFieldValueChange = { updatedTextFieldValue ->
            localTextFieldValue = updatedTextFieldValue
        },
        onDismissRequest = {
            showEditCustomUserAgentDialog.value = false
            focusManager.clearFocus()
        },
        onConfirm = {
            onConfirm(localTextFieldValue.text)
            focusManager.clearFocus()
            showEditCustomUserAgentDialog.value = false
        },
        singleLine = true,
        keyboardActions =
            KeyboardActions(
                onDone = {
                    onConfirm(localTextFieldValue.text)
                    focusManager.clearFocus()
                    showEditCustomUserAgentDialog.value = false
                }
            ),
    )
}

@Composable
private fun PageScaleDialog(
    show: Boolean,
    pageScale: Float,
    onPageScaleChange: (Float) -> Unit,
    onApply: (Float) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var scaleText by
        remember(show, pageScale) { mutableStateOf((pageScale * 100).toInt().toString()) }

    AppTextFieldDialog(
        show = show,
        title = MLang.AppSettings.Interface.PageScaleTitle,
        value = scaleText,
        onValueChange = { value ->
            if (value.isEmpty() || value.all(Char::isDigit)) {
                scaleText = value
            }
        },
        onDismissRequest = onDismissRequest,
        onConfirm = {
            val parsedPercent = scaleText.toFloatOrNull() ?: (pageScale * 100)
            val clampedScale = parsedPercent.coerceIn(80f, 120f) / 100f
            onPageScaleChange(clampedScale)
            onApply(clampedScale)
            onDismissRequest()
        },
        summary = MLang.AppSettings.Interface.PageScaleDialogSummary,
        renderInRootScaffold = true,
        singleLine = true,
        trailingIcon = {
            Text(
                text = "%",
                modifier = Modifier.padding(horizontal = UiDp.dp16),
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
    )
}
