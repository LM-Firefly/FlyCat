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

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.data.AppLogSettings
import com.github.yumelira.yumebox.core.data.Preference
import com.github.yumelira.yumebox.core.model.AppColorTheme
import com.github.yumelira.yumebox.core.model.AppLanguage
import com.github.yumelira.yumebox.core.model.ThemeMode
import com.github.yumelira.yumebox.core.util.moeWallpaperFile
import com.github.yumelira.yumebox.data.controller.AppSettingsController
import com.github.yumelira.yumebox.data.store.AppStateManager
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.theme.DEFAULT_CUSTOM_THEME_SEED_ARGB
import com.github.yumelira.yumebox.update.GitHubUpdateManager
import com.github.yumelira.yumebox.update.UpdateSource
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSettingsViewModel(
    private val application: Application,
    appStateManager: AppStateManager,
    private val controller: AppSettingsController,
    private val updateManager: GitHubUpdateManager,
    private val appLogSettings: AppLogSettings,
) : ViewModel() {
    private val settings = appStateManager.appSettingsStore
    private val featureStore = appStateManager.featureStore

    val initialSetupCompleted: Preference<Boolean> = settings.initialSetupCompleted
    val privacyPolicyAccepted: Preference<Boolean> = settings.privacyPolicyAccepted

    val themeMode: Preference<ThemeMode> = settings.themeMode
    val appLanguage: Preference<AppLanguage> = settings.appLanguage
    val colorTheme: Preference<AppColorTheme> = settings.colorTheme
    val themeSeedColorArgb: Preference<Long> = settings.themeAccentColorArgb
    val invertOnPrimaryColors: Preference<Boolean> = settings.invertOnPrimaryColors
    val automaticRestart: Preference<Boolean> = settings.automaticRestart
    val autoUpdateCurrentProfileOnStart: Preference<Boolean> =
        settings.autoUpdateCurrentProfileOnStart
    val hideAppIcon: Preference<Boolean> = settings.hideAppIcon
    val excludeFromRecents: Preference<Boolean> = settings.excludeFromRecents
    val showTrafficNotification: Preference<Boolean> = settings.showTrafficNotification
    val bottomBarAutoHide: Preference<Boolean> = settings.bottomBarAutoHide
    val topBarBlurEnabled: Preference<Boolean> = settings.topBarBlurEnabled
    val classicHomeEnabled: Preference<Boolean> = settings.classicHomeEnabled
    val moeWallpaperUri: Preference<String> = settings.moeWallpaperUri
    val moeWallpaperSourceUri: Preference<String> = settings.moeWallpaperSourceUri
    val moeWallpaperZoom: Preference<Float> = settings.moeWallpaperZoom
    val moeWallpaperBiasX: Preference<Float> = settings.moeWallpaperBiasX
    val moeWallpaperBiasY: Preference<Float> = settings.moeWallpaperBiasY
    val moeHomeQuote: Preference<String> = settings.moeHomeQuote
    val moeHomeQuoteAuthor: Preference<String> = settings.moeHomeQuoteAuthor
    val moeSidebarExpanded: Preference<Boolean> = settings.moeSidebarExpanded
    val pageScale: Preference<Float> = settings.pageScale
    val singleNodeTest: Preference<Boolean> = settings.singleNodeTest
    val logLevel: Preference<Int> = settings.logLevel
    val autoCheckAppUpdate: Preference<Boolean> = settings.autoCheckAppUpdate
    val exitUiWhenBackground: Preference<Boolean> = featureStore.exitUiWhenBackground

    private val _updateSource = MutableStateFlow(updateManager.getSelectedSource())
    val updateSource: StateFlow<UpdateSource> = _updateSource

    val customUserAgent: Preference<String> = settings.customUserAgent

    data class MainScreenSettings(
        val bottomBarAutoHide: Boolean,
        val topBarBlurEnabled: Boolean,
        val moeMainUiEnabled: Boolean,
        val moeWallpaperUri: String,
        val moeWallpaperZoom: Float,
        val moeWallpaperBiasX: Float,
        val moeWallpaperBiasY: Float,
    )

    private data class DisplayPrefs(
        val bottomBarAutoHide: Boolean,
        val topBarBlurEnabled: Boolean,
        val moeMainUiEnabled: Boolean,
    )

    private data class WallpaperPrefs(
        val uri: String,
        val zoom: Float,
        val biasX: Float,
        val biasY: Float,
    )

    val mainScreenSettings: StateFlow<MainScreenSettings> = combine(
        combine(
            bottomBarAutoHide.state,
            topBarBlurEnabled.state,
            classicHomeEnabled.state,
            ::DisplayPrefs,
        ),
        combine(
            moeWallpaperUri.state,
            moeWallpaperZoom.state,
            moeWallpaperBiasX.state,
            moeWallpaperBiasY.state,
            ::WallpaperPrefs,
        ),
    ) { display, wallpaper ->
        MainScreenSettings(
            bottomBarAutoHide = display.bottomBarAutoHide,
            topBarBlurEnabled = display.topBarBlurEnabled,
            moeMainUiEnabled = display.moeMainUiEnabled,
            moeWallpaperUri = wallpaper.uri,
            moeWallpaperZoom = wallpaper.zoom,
            moeWallpaperBiasX = wallpaper.biasX,
            moeWallpaperBiasY = wallpaper.biasY,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MainScreenSettings(
            bottomBarAutoHide = bottomBarAutoHide.value,
            topBarBlurEnabled = topBarBlurEnabled.value,
            moeMainUiEnabled = classicHomeEnabled.value,
            moeWallpaperUri = moeWallpaperUri.value,
            moeWallpaperZoom = moeWallpaperZoom.value,
            moeWallpaperBiasX = moeWallpaperBiasX.value,
            moeWallpaperBiasY = moeWallpaperBiasY.value,
        ),
    )

    fun onThemeModeChange(mode: ThemeMode) = themeMode.set(mode)

    fun onAppLanguageChange(language: AppLanguage) = controller.applyAppLanguage(language)

    fun onColorThemeChange(theme: AppColorTheme) = colorTheme.set(theme)

    fun onThemeSeedColorChange(argb: Long) = themeSeedColorArgb.set(argb)

    fun onInvertOnPrimaryColorsChange(enabled: Boolean) = invertOnPrimaryColors.set(enabled)

    fun resetThemeSeedColor() = themeSeedColorArgb.set(DEFAULT_CUSTOM_THEME_SEED_ARGB)

    fun onBottomBarAutoHideChange(enabled: Boolean) = bottomBarAutoHide.set(enabled)

    fun onTopBarBlurEnabledChange(enabled: Boolean) = topBarBlurEnabled.set(enabled)

    fun onClassicHomeEnabledChange(enabled: Boolean) = classicHomeEnabled.set(enabled)

    fun onMoeWallpaperUriChange(uri: String) = moeWallpaperUri.set(uri)

    /**
     * Persists the selected Moe wallpaper by copying [sourceUri] into the app-private files dir and
     * storing the resulting `file://` path as [moeWallpaperUri], while remembering the original
     * source in [moeWallpaperSourceUri] for lazy re-import. If the copy fails the original source
     * URI is persisted directly (degraded but working) and a toast is shown.
     */
    fun applyMoeWallpaper(sourceUri: String, onApplied: () -> Unit) {
        viewModelScope.launch {
            val localPath = MoeWallpaperImporter.importToLocal(application, sourceUri)
            if (localPath != null) {
                moeWallpaperUri.set(localPath)
                moeWallpaperSourceUri.set(sourceUri)
            } else {
                moeWallpaperUri.set(sourceUri)
                moeWallpaperSourceUri.set(sourceUri)
                application.toast(MLang.AppSettings.Interface.HomeWallpaperImportFailed)
            }
            onApplied()
        }
    }

    fun onMoeWallpaperCropChange(zoom: Float, biasX: Float, biasY: Float) {
        moeWallpaperZoom.set(zoom.coerceIn(1f, 5f))
        moeWallpaperBiasX.set(biasX.coerceIn(-1f, 1f))
        moeWallpaperBiasY.set(biasY.coerceIn(-1f, 1f))
    }

    fun onMoeHomeQuoteChange(quote: String) = moeHomeQuote.set(quote)

    fun onMoeHomeQuoteAuthorChange(author: String) = moeHomeQuoteAuthor.set(author)

    fun onMoeSidebarExpandedChange(expanded: Boolean) = moeSidebarExpanded.set(expanded)

    fun clearMoeWallpaperUri() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { application.moeWallpaperFile().delete() } }
            moeWallpaperUri.set("")
            moeWallpaperSourceUri.set("")
            onMoeWallpaperCropChange(zoom = 1f, biasX = 0f, biasY = 0f)
        }
    }

    fun onPageScaleChange(scale: Float) = pageScale.set(scale)

    fun onAutomaticRestartChange(enabled: Boolean) = automaticRestart.set(enabled)

    fun onAutoUpdateCurrentProfileOnStartChange(enabled: Boolean) =
        autoUpdateCurrentProfileOnStart.set(enabled)

    fun onHideAppIconChange(hide: Boolean) = hideAppIcon.set(hide)

    fun onExcludeFromRecentsChange(exclude: Boolean) = excludeFromRecents.set(exclude)

    fun onShowTrafficNotificationChange(show: Boolean) = showTrafficNotification.set(show)

    fun onSingleNodeTestChange(enabled: Boolean) = singleNodeTest.set(enabled)

    fun onAutoCheckAppUpdateChange(enabled: Boolean) = autoCheckAppUpdate.set(enabled)

    fun onUpdateSourceChange(source: UpdateSource) {
        if (_updateSource.value == source) return
        updateManager.setSelectedSource(source)
        _updateSource.value = source
    }

    fun onExitUiWhenBackgroundChange(enabled: Boolean) = exitUiWhenBackground.set(enabled)

    fun applyCustomUserAgent(userAgent: String) = controller.applyCustomUserAgent(userAgent)

    fun onLogLevelChange(level: Int) {
        logLevel.set(level)
        appLogSettings.minLogLevel = level
    }
    fun setInitialSetupCompleted(completed: Boolean) = initialSetupCompleted.set(completed)

    fun setPrivacyPolicyAccepted(accepted: Boolean) = privacyPolicyAccepted.set(accepted)
}
