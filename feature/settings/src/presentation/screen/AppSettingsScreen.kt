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

package com.github.lmfirefly.flycat.feature.settings.presentation.screen

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.model.AppLanguage
import com.github.lmfirefly.flycat.core.model.NotificationLineContent
import com.github.lmfirefly.flycat.core.model.ThemeMode
import com.github.lmfirefly.flycat.core.model.UpdateSource
import com.github.lmfirefly.flycat.feature.settings.presentation.screen.component.ThemeColorPickerItem
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassScaffold
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.dialog.AppDialog
import com.github.lmfirefly.flycat.presentation.component.dialog.AppTextFieldDialog
import com.github.lmfirefly.flycat.presentation.component.dialog.DialogButtonRow
import com.github.lmfirefly.flycat.presentation.component.dialog.WarningBottomSheet
import com.github.lmfirefly.flycat.presentation.component.input.GlassTextField
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceArrowItem
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceEnumItem
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceSwitchItem
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceValueItem
import com.github.lmfirefly.flycat.presentation.component.misc.Title
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalNavigator
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.navigation.Route
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.toast
import com.github.lmfirefly.flycat.ui.platform.AppIconUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppSettingsScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<AppSettingsViewModel>()
    GlassScaffold(
        topBar = { TopBar(title = FlyTxt.AppSettings.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(navigator = navigator) }) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item { AppBehaviorSettingsSection(viewModel) }
            item { AppInterfaceSettingsSection(viewModel = viewModel) }
            item { AppNavigationSettingsSection(viewModel) }
            item { AppPrivacySettingsSection(viewModel) }
            item { AppServiceSettingsSection(viewModel, navigator) }
            item { AppNetworkSettingsSection(viewModel) }
        }
    }
}

@Composable
private fun AppBehaviorSettingsSection(viewModel: AppSettingsViewModel) {
    val automaticRestart by viewModel.automaticRestart.state.collectAsStateWithLifecycle()
    val autoUpdateCurrentProfileOnStart by viewModel.autoUpdateCurrentProfileOnStart.state.collectAsStateWithLifecycle()
    Title(FlyTxt.AppSettings.Section.Behavior)
    Card {
        PreferenceSwitchItem(
            title = FlyTxt.AppSettings.Behavior.AutoStartTitle,
            summary = FlyTxt.AppSettings.Behavior.AutoStartSummary,
            checked = automaticRestart,
            onCheckedChange = viewModel::onAutomaticRestartChange,
        )
        PreferenceSwitchItem(
            title = FlyTxt.AppSettings.Behavior.AutoUpdateOnStartTitle,
            summary = FlyTxt.AppSettings.Behavior.AutoUpdateOnStartSummary,
            checked = autoUpdateCurrentProfileOnStart,
            onCheckedChange = viewModel::onAutoUpdateCurrentProfileOnStartChange,
        )
    }
}

@Composable
private fun AppInterfaceSettingsSection(viewModel: AppSettingsViewModel) {
    val themeMode by viewModel.themeMode.state.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.state.collectAsStateWithLifecycle()
    val themeSeedColorArgb by viewModel.themeSeedColorArgb.state.collectAsStateWithLifecycle()
    val invertOnPrimaryColors by viewModel.invertOnPrimaryColors.state.collectAsStateWithLifecycle()
    val bottomBarAutoHide by viewModel.bottomBarAutoHide.state.collectAsStateWithLifecycle()
    val pageScale by viewModel.pageScale.state.collectAsStateWithLifecycle()
    val classicHomeEnabled by viewModel.classicHomeEnabled.state.collectAsStateWithLifecycle()
    val homeHitokotoEnabled by viewModel.homeHitokotoEnabled.state.collectAsStateWithLifecycle()
    val homeQuote by viewModel.moeHomeQuote.state.collectAsStateWithLifecycle()
    val homeQuoteAuthor by viewModel.moeHomeQuoteAuthor.state.collectAsStateWithLifecycle()
    val homeQuoteSummary = remember(homeQuote) { homeQuote.ifBlank { FlyTxt.AppSettings.Interface.HomeQuoteDefault } }
    val homeQuoteAuthorSummary = remember(homeQuoteAuthor) { homeQuoteAuthor.ifBlank { FlyTxt.AppSettings.Interface.HomeQuoteAuthorDefault } }
    val navigator = LocalNavigator.current
    var showAppBackgroundSheet by remember { mutableStateOf(false) }
    var showCardEffectsSheet by remember { mutableStateOf(false) }

    Title(FlyTxt.AppSettings.Interface.ColorThemeTitle)
    Card {
        PreferenceEnumItem(
            title = FlyTxt.AppSettings.Interface.ThemeModeTitle,
            summary = FlyTxt.AppSettings.Interface.ThemeModeSummary,
            currentValue = themeMode,
            items = listOf(
                FlyTxt.AppSettings.Interface.ThemeModeSystem,
                FlyTxt.AppSettings.Interface.ThemeModeLight,
                FlyTxt.AppSettings.Interface.ThemeModeDark,
            ),
            values = ThemeMode.entries,
            onValueChange = viewModel::onThemeModeChange,
        )
        PreferenceSwitchItem(
            title = FlyTxt.AppSettings.Interface.ThemeColorPolarityInvertTitle,
            summary = FlyTxt.AppSettings.Interface.ThemeColorPolarityInvertSummary,
            checked = invertOnPrimaryColors,
            onCheckedChange = viewModel::onInvertOnPrimaryColorsChange,
        )
        ThemeColorPickerItem(
            themeSeedColorArgb = themeSeedColorArgb,
            onThemeSeedColorChange = viewModel::onThemeSeedColorChange,
        )
    }
    Title(FlyTxt.AppSettings.Section.Interface)
    Card {
        PreferenceEnumItem(
            title = FlyTxt.AppSettings.Interface.LanguageTitle,
            summary = FlyTxt.AppSettings.Interface.LanguageSummary,
            currentValue = appLanguage,
            items = listOf(
                FlyTxt.AppSettings.Interface.LanguageSystem,
                FlyTxt.AppSettings.Interface.LanguageChinese,
                FlyTxt.AppSettings.Interface.LanguageChineseTraditional,
                FlyTxt.AppSettings.Interface.LanguageEnglish,
                FlyTxt.AppSettings.Interface.LanguageJapanese,
                FlyTxt.AppSettings.Interface.LanguageRussian,
            ),
            values = AppLanguage.entries,
            onValueChange = viewModel::onAppLanguageChange,
        )
        // 全局背景（模式／壁纸来源／渐进模糊）设置入口。
        ArrowPreference(
            title = FlyTxt.AppSettings.Interface.AppBackgroundTitle,
            summary = FlyTxt.AppSettings.Interface.AppBackgroundSummary,
            onClick = { showAppBackgroundSheet = true },
        )
        PreferenceSwitchItem(
            title = FlyTxt.AppSettings.Interface.AutoHideNavbarTitle,
            summary = FlyTxt.AppSettings.Interface.AutoHideNavbarSummary,
            checked = bottomBarAutoHide,
            onCheckedChange = viewModel::onBottomBarAutoHideChange,
        )
        PageScalePreferenceItem(pageScale = pageScale, onApply = viewModel::onPageScaleChange)
        ArrowPreference(
            title = FlyTxt.AppSettings.BlurEffects.CardEffectsTitle,
            summary = FlyTxt.AppSettings.BlurEffects.CardEffectsSummary,
            onClick = { showCardEffectsSheet = true },
        )
    }
    WallpaperBlurSheet(show = showAppBackgroundSheet, onDismiss = { showAppBackgroundSheet = false }, navigator = navigator)
    CardEffectsSheet(show = showCardEffectsSheet, onDismiss = { showCardEffectsSheet = false })
    Title(FlyTxt.AppSettings.Section.Home)
    Card {
        PreferenceSwitchItem(
            title = FlyTxt.AppSettings.Interface.ClassicHomeTitle,
            checked = classicHomeEnabled,
            onCheckedChange = viewModel::onClassicHomeEnabledChange,
        )
        AnimatedVisibility(visible = classicHomeEnabled) {
            PreferenceSwitchItem(
                title = FlyTxt.AppSettings.Interface.HitokotoTitle,
                summary = FlyTxt.AppSettings.Interface.HitokotoSummary,
                checked = homeHitokotoEnabled,
                onCheckedChange = viewModel::onHomeHitokotoEnabledChange,
            )
        }
        AnimatedVisibility(visible = !classicHomeEnabled || homeHitokotoEnabled) {
            Column {
                MoeQuotePreferenceItem(
                    title = FlyTxt.AppSettings.Interface.HomeQuoteTitle,
                    summary = homeQuoteSummary,
                    dialogTitle = FlyTxt.AppSettings.Interface.EditHomeQuoteTitle,
                    currentValue = homeQuote,
                    onConfirm = viewModel::onMoeHomeQuoteChange,
                )
                MoeQuotePreferenceItem(
                    title = FlyTxt.AppSettings.Interface.HomeQuoteAuthorTitle,
                    summary = homeQuoteAuthorSummary,
                    dialogTitle = FlyTxt.AppSettings.Interface.EditHomeQuoteAuthorTitle,
                    currentValue = homeQuoteAuthor,
                    onConfirm = viewModel::onMoeHomeQuoteAuthorChange,
                )
            }
        }
    }
}

@Composable
private fun AppNavigationSettingsSection(viewModel: AppSettingsViewModel) {
    val context = LocalContext.current
    val predictiveBackEnabled by viewModel.predictiveBackEnabled.state.collectAsStateWithLifecycle()
    val predictiveBackMaxProgress by viewModel.predictiveBackMaxProgress.state.collectAsStateWithLifecycle()

    Title(FlyTxt.AppSettings.Section.Navigation)
    Card {
        PreferenceSwitchItem(
            title = FlyTxt.AppSettings.PredictiveBack.Title,
            checked = predictiveBackEnabled,
            onCheckedChange = { enabled ->
                viewModel.onPredictiveBackEnabledChange(enabled)
                context.toast(FlyTxt.AppSettings.PredictiveBack.RestartSummary)
            },
        )
        PredictiveBackProgressPreferenceItem(
            progress = predictiveBackMaxProgress,
            onApply = viewModel::onPredictiveBackMaxProgressChange,
        )
    }
}

@Composable
private fun PredictiveBackProgressPreferenceItem(
    progress: Float,
    onApply: (Float) -> Unit,
) {
    var localProgress by remember(progress) { mutableFloatStateOf(progress) }

    PreferenceArrowItem(
        title = FlyTxt.AppSettings.PredictiveBack.ProgressTitle,
        endActions = {
            Text(
                text = "${localProgress.toInt()}%",
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = {},
        bottomAction = {
            Slider(
                value = localProgress,
                onValueChange = { localProgress = it },
                onValueChangeFinished = { onApply(localProgress) },
                valueRange = 1f..100f,
                magnetThreshold = 0.01f,
                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            )
        },
    )
}

@Composable
private fun AppPrivacySettingsSection(viewModel: AppSettingsViewModel) {
    val context = LocalContext.current
    val excludeFromRecents by viewModel.excludeFromRecents.state.collectAsStateWithLifecycle()

    Title(FlyTxt.AppSettings.Section.Privacy)
    Card {
        HideAppIconPreferenceItem(
            hideAppIconFlow = viewModel.hideAppIcon.state,
            onHideAppIconChange = viewModel::onHideAppIconChange,
            context = context,
        )
        PreferenceSwitchItem(
            title = FlyTxt.AppSettings.Privacy.HideFromRecentsTitle,
            summary = FlyTxt.AppSettings.Privacy.HideFromRecentsSummary,
            checked = excludeFromRecents,
            onCheckedChange = viewModel::onExcludeFromRecentsChange,
        )
    }
}

@Composable
private fun AppServiceSettingsSection(viewModel: AppSettingsViewModel, navigator: Navigator) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val showTrafficNotification by viewModel.showTrafficNotification.state.collectAsStateWithLifecycle()
    val superIslandEnabled by viewModel.superIslandEnabled.state.collectAsStateWithLifecycle()
    val liveNotificationEnabled by viewModel.liveNotificationEnabled.state.collectAsStateWithLifecycle()
    val shizukuAccess by viewModel.shizukuAccess.collectAsStateWithLifecycle()
    val privilegeAccess by viewModel.privilegeAccess.collectAsStateWithLifecycle()
    val exitUiWhenBackground by viewModel.exitUiWhenBackground.state.collectAsStateWithLifecycle()
    var batteryOptimizationIgnored by remember {
        mutableStateOf(isBatteryOptimizationIgnored(context))
    }
    val scope = rememberCoroutineScope()
    // 通过 activity-result 启动，这样摘要会在系统授权对话框返回后立即刷新——HyperOS 将其显示为对话框样式的 activity，ON_RESUME 的时序不可靠。
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryOptimizationIgnored = isBatteryOptimizationIgnored(context)
        scope.launch {
            // PowerManager 白名单状态可能稍微滞后于对话框的结果。
            delay(500)
            batteryOptimizationIgnored = isBatteryOptimizationIgnored(context)
        }
    }
    val batteryOptimizationSummary = remember(batteryOptimizationIgnored) {
        if (batteryOptimizationIgnored) {
            FlyTxt.AppSettings.ServiceSection.BatteryOptimizationSummaryEnabled
        } else {
            FlyTxt.AppSettings.ServiceSection.BatteryOptimizationSummaryDisabled
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimizationIgnored = isBatteryOptimizationIgnored(context)
                viewModel.refreshShizukuAccess()
                viewModel.refreshPrivilegeAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Title(FlyTxt.AppSettings.Section.Service)
    Card {
        PreferenceSwitchItem(
            title = FlyTxt.AppSettings.ServiceSection.TrafficNotificationTitle,
            summary = FlyTxt.AppSettings.ServiceSection.TrafficNotificationSummary,
            checked = showTrafficNotification,
            onCheckedChange = viewModel::onShowTrafficNotificationChange,
        )
        PreferenceSwitchItem(
            title = FlyTxt.AppSettings.ServiceSection.ExitUiWhenBackgroundTitle,
            summary = FlyTxt.AppSettings.ServiceSection.ExitUiWhenBackgroundSummary,
            checked = exitUiWhenBackground,
            onCheckedChange = viewModel::onExitUiWhenBackgroundChange,
        )
        PreferenceArrowItem(
            title = FlyTxt.AppSettings.ServiceSection.PrivilegeTitle,
            endActions = {
                Text(
                    text = privilegeAccess.statusText,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
            onClick = { navigator.push(Route.PrivilegedService) },
        )
        // 超级岛是 HyperOS 灵动岛特性，仅在支持的设备上提供开关。
        if (shizukuAccess.islandSupported) {
            PreferenceSwitchItem(
                title = FlyTxt.AppSettings.ServiceSection.SuperIslandTitle,
                checked = superIslandEnabled,
                onCheckedChange = viewModel::onSuperIslandEnabledChange,
            )
        }
        // Android 16 实时通知（promoted ongoing）仅在 Android 16+ 设备提供开关；与超级岛互斥展示。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            PreferenceSwitchItem(
                title = FlyTxt.AppSettings.ServiceSection.LiveNotificationTitle,
                summary = FlyTxt.AppSettings.ServiceSection.LiveNotificationSummary,
                checked = liveNotificationEnabled,
                onCheckedChange = viewModel::onLiveNotificationEnabledChange,
            )
        }
        PreferenceArrowItem(
            title = FlyTxt.AppSettings.ServiceSection.BatteryOptimizationTitle,
            summary = batteryOptimizationSummary,
            onClick = {
                val launched = batteryOptimizationIntents(context, batteryOptimizationIgnored).any { intent ->
                    runCatching { batteryOptimizationLauncher.launch(intent) }.isSuccess
                }
                if (!launched) {
                    context.toast(FlyTxt.Util.Error.UnknownError)
                }
            },
        )
        // 通知内容作为服务子项：进入二级页设置（含实时预览）。
        PreferenceArrowItem(
            title = FlyTxt.AppSettings.Notification.ContentSection,
            summary = FlyTxt.AppSettings.Notification.ContentSummary,
            onClick = { navigator.push(Route.NotificationSettings) },
        )
    }
}

@Composable
private fun AppNetworkSettingsSection(viewModel: AppSettingsViewModel) {
    val updateSource by viewModel.updateSource.collectAsStateWithLifecycle()
    val autoCheckAppUpdate by viewModel.autoCheckAppUpdate.state.collectAsStateWithLifecycle()
    val customUserAgent by viewModel.customUserAgent.state.collectAsStateWithLifecycle()

    Title(FlyTxt.AppSettings.Section.Network)
    Card {
        PreferenceEnumItem(
            title = FlyTxt.AppSettings.Network.UpdateChannelTitle,
            summary = FlyTxt.AppSettings.Network.UpdateChannelSummary,
            currentValue = updateSource,
            items = listOf(
                FlyTxt.AppSettings.Network.UpdateChannelStable,
                FlyTxt.AppSettings.Network.UpdateChannelPre,
                FlyTxt.AppSettings.Network.UpdateChannelSmart,
            ),
            values = listOf(
                UpdateSource.Latest,
                UpdateSource.Prerelease,
                UpdateSource.Smart,
            ),
            onValueChange = viewModel::onUpdateSourceChange,
        )
        PreferenceSwitchItem(
            title = FlyTxt.AppSettings.Network.AutoCheckAppUpdateTitle,
            summary = FlyTxt.AppSettings.Network.AutoCheckAppUpdateSummary,
            checked = autoCheckAppUpdate,
            onCheckedChange = viewModel::onAutoCheckAppUpdateChange,
        )
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
    val hideAppIcon by hideAppIconFlow.collectAsStateWithLifecycle()
    val showHideIconDialogState = remember { mutableStateOf(false) }

    PreferenceSwitchItem(
        title = FlyTxt.AppSettings.Privacy.HideIconTitle,
        summary = FlyTxt.AppSettings.Privacy.HideIconSummary,
        checked = hideAppIcon,
        onCheckedChange = { checked ->
            if (checked) {
                showHideIconDialogState.value = true
            } else {
                onHideAppIconChange(false)
                AppIconUtils.toggleIcon(context, false)
            }
        },
    )

    WarningBottomSheet(
        show = showHideIconDialogState,
        title = FlyTxt.AppSettings.WarningDialog.Title,
        messages = listOf(
            FlyTxt.AppSettings.WarningDialog.HideIconMsg1,
            FlyTxt.AppSettings.WarningDialog.HideIconMsg2,
        ),
        onConfirm = {
            onHideAppIconChange(true)
            AppIconUtils.toggleIcon(context, true)
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
    val textFieldState = rememberTextFieldState(initialText = currentValue)
    fun confirm() {
        onConfirm(textFieldState.text.toString())
        focusManager.clearFocus()
        showEditDialog.value = false
    }
    PreferenceValueItem(
        title = title,
        summary = summary,
        onClick = {
            textFieldState.setTextAndPlaceCursorAtEnd(currentValue)
            showEditDialog.value = true
        },
    )
    AppTextFieldDialog(
        show = showEditDialog.value,
        title = dialogTitle,
        state = textFieldState,
        onDismissRequest = {
            showEditDialog.value = false
            focusManager.clearFocus()
        },
        onConfirm = { confirm() },
        lineLimits = TextFieldLineLimits.SingleLine,
        onKeyboardAction = KeyboardActionHandler { confirm() },
    )
}

internal fun isBatteryOptimizationIgnored(context: android.content.Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

internal fun batteryOptimizationIntents(
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
        // 已在白名单中：请打开本应用自身的功耗策略页面，而非全局"所有应用的电量使用情况"列表。优先打开MIUI/HyperOS的单应用省电管理页面...
        add(
            Intent().apply {
                component = ComponentName(
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
    // ……然后将公共的应用详情页作为两个分支的回退方案。
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
        title = FlyTxt.AppSettings.Interface.PageScaleTitle,
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
    val customUserAgentSummary = remember(customUserAgent) { customUserAgent.ifEmpty { FlyTxt.AppSettings.Network.CustomUserAgentSummaryDefault } }
    val showEditCustomUserAgentDialog = remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val textFieldState = rememberTextFieldState(initialText = customUserAgent)
    fun confirm() {
        onConfirm(textFieldState.text.toString())
        focusManager.clearFocus()
        showEditCustomUserAgentDialog.value = false
    }
    PreferenceArrowItem(
        title = FlyTxt.AppSettings.Network.CustomUserAgentTitle,
        summary = customUserAgentSummary,
        onClick = {
            textFieldState.setTextAndPlaceCursorAtEnd(customUserAgent)
            showEditCustomUserAgentDialog.value = true
        },
        holdDownState = showEditCustomUserAgentDialog.value,
    )
    AppTextFieldDialog(
        show = showEditCustomUserAgentDialog.value,
        title = FlyTxt.AppSettings.EditDialog.UserAgentTitle,
        state = textFieldState,
        onDismissRequest = {
            showEditCustomUserAgentDialog.value = false
            focusManager.clearFocus()
        },
        onConfirm = { confirm() },
        lineLimits = TextFieldLineLimits.SingleLine,
        onKeyboardAction = KeyboardActionHandler { confirm() },
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
    val scaleState = rememberTextFieldState()
    val scaleInputTransformation = remember {
        InputTransformation {
            val filtered = toString().filter(Char::isDigit)
            if (filtered != toString()) replace(0, length, filtered)
        }
    }
    LaunchedEffect(show) {
        if (show) scaleState.setTextAndPlaceCursorAtEnd((pageScale * 100).toInt().toString())
    }
    AppDialog(
        show = show,
        title = FlyTxt.AppSettings.Interface.PageScaleTitle,
        summary = FlyTxt.AppSettings.Interface.PageScaleDialogSummary,
        onDismissRequest = onDismissRequest,
        renderInRootScaffold = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiDp.dp16)) {
            GlassTextField(
                state = scaleState,
                modifier = Modifier.fillMaxWidth().padding(bottom = UiDp.dp8),
                inputTransformation = scaleInputTransformation,
                lineLimits = TextFieldLineLimits.SingleLine,
                trailingIcon = {
                    Text(
                        text = "%",
                        modifier = Modifier.padding(horizontal = UiDp.dp16),
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                },
            )
            DialogButtonRow(
                onCancel = onDismissRequest,
                onConfirm = {
                    val scaleText = scaleState.text.toString()
                    val parsedPercent = scaleText.toFloatOrNull() ?: (pageScale * 100)
                    val clampedScale = parsedPercent.coerceIn(80f, 120f) / 100f
                    onPageScaleChange(clampedScale)
                    onApply(clampedScale)
                    onDismissRequest()
                },
            )
        }
    }
}
