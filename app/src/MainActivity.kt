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

package com.github.yumelira.yumebox

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.github.yumelira.yumebox.common.util.AppLanguageManager
import com.github.yumelira.yumebox.core.util.APPLICATION_SCOPE_NAME
import com.github.yumelira.yumebox.core.util.PendingImportUrlHolder
import com.github.yumelira.yumebox.feature.home.presentation.screen.moe.HomePreviewGuideDialog
import com.github.yumelira.yumebox.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.yumelira.yumebox.presentation.component.LocalTopBarHazeState
import com.github.yumelira.yumebox.presentation.component.LocalTopBarHazeStyle
import com.github.yumelira.yumebox.presentation.component.ToastDialogHost
import com.github.yumelira.yumebox.presentation.navigation.AppNavContainer
import com.github.yumelira.yumebox.presentation.theme.ProvideAndroidPlatformTheme
import com.github.yumelira.yumebox.presentation.theme.YumeTheme
import com.github.yumelira.yumebox.runtime.client.common.util.IntentController
import com.github.yumelira.yumebox.runtime.client.common.util.extractPendingImportUrl
import com.tencent.mmkv.MMKV
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : FragmentActivity() {
    companion object {
        private const val REQUEST_STARTUP_PERMISSIONS = 1001
        private const val MIUI_GET_INSTALLED_APPS_PERMISSION =
            "com.android.permission.GET_INSTALLED_APPS"
        private const val EXTRA_EXIT_UI_WHEN_BACKGROUND = "exit_ui_when_background"
        val pendingImportUrl: StateFlow<String?> = com.github.yumelira.yumebox.core.util.PendingImportUrlHolder.pendingImportUrl

        fun clearPendingImportUrl() {
            com.github.yumelira.yumebox.core.util.PendingImportUrlHolder.clear()
        }

        private val _pendingDeepLink = MutableStateFlow<String?>(null)
        val pendingDeepLink: StateFlow<String?> = _pendingDeepLink.asStateFlow()

        fun clearPendingDeepLink() {
            _pendingDeepLink.value = null
        }
    }

    private val appSettingsReader: com.github.yumelira.yumebox.core.contract.AppSettingsReader by
        inject()
    private val appSettingsStore: com.github.yumelira.yumebox.data.store.AppSettingsStore by inject()
    private val featureStoreReader: com.github.yumelira.yumebox.core.contract.FeatureStoreReader by inject()
    private val networkSettingsStorage:
        com.github.yumelira.yumebox.data.store.NetworkSettingsStore by
        inject()
    private val profilesRepository: com.github.yumelira.yumebox.runtime.client.ProfilesRepository by
        inject()
    private val proxyFacade: com.github.yumelira.yumebox.runtime.client.ProxyFacade by inject()
    private val serviceCache: MMKV by inject(qualifier = named("service_cache"))
    private val applicationScope: CoroutineScope by
        inject(qualifier = named(APPLICATION_SCOPE_NAME))

    private lateinit var intentController: IntentController

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)
        applyExcludeFromRecents(appSettingsReader.excludeFromRecents.value)
        intentController = IntentController(lifecycleScope, packageName)
        if (intent?.action?.endsWith(".action.START_CLASH") == true ||
            intent?.action?.endsWith(".action.STOP_CLASH") == true) {
            // Use application scope so the coroutine survives finish()
            IntentController(applicationScope, packageName).handleIntent(intent)
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            finish()
            return
        }

        handleIntent(intent)

        requestStartupPermissions()

        val showHomeGuideInitially =
            savedInstanceState == null && !appSettingsStore.homePreviewGuideShown.value
        if (showHomeGuideInitially) {
            appSettingsStore.homePreviewGuideShown.set(true)
        }

        setContent {
            val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
            val activitySettings by appSettingsViewModel.activitySettings.collectAsStateWithLifecycle()
            LaunchedEffect(activitySettings.excludeFromRecents) {
                this@MainActivity.applyExcludeFromRecents(activitySettings.excludeFromRecents)
            }

            ProvideAndroidPlatformTheme {
                val systemDensity = LocalDensity.current
                val scaledDensity =
                    remember(systemDensity, activitySettings.pageScale) {
                        Density(systemDensity.density * activitySettings.pageScale, systemDensity.fontScale)
                    }
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    YumeTheme(
                        themeMode = activitySettings.themeMode,
                        themeSeedColorArgb = activitySettings.themeSeedColorArgb,
                        invertOnPrimaryColors = activitySettings.invertOnPrimaryColors,
                    ) {
                        val topBarHazeState = remember { HazeState() }
                        val topBarBackground = MiuixTheme.colorScheme.surface
                        val topBarHazeStyle =
                            remember(topBarBackground) {
                                HazeBlurStyle(
                                    backgroundColor = topBarBackground,
                                    colorEffects = listOf(HazeColorEffect.tint(topBarBackground.copy(0.8f))),
                                )
                            }
                        CompositionLocalProvider(
                            LocalTopBarHazeState provides
                                if (activitySettings.topBarBlurEnabled) topBarHazeState else null,
                            LocalTopBarHazeStyle provides
                                if (activitySettings.topBarBlurEnabled) topBarHazeStyle else null,
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MiuixTheme.colorScheme.surface,
                            ) {
                                AppNavContainer()
                                ToastDialogHost()

                                var showHomeGuide by
                                    remember { mutableStateOf(showHomeGuideInitially) }
                                HomePreviewGuideDialog(
                                    show = showHomeGuide,
                                    onDismissRequest = { showHomeGuide = false },
                                )
                            }
                        }
                    }
                }
            }
        }
        // Auto-start is handled exclusively by App.scheduleDeferredStartupTasks() to avoid duplicate invocations.
        // The AutoStartSessionGate ensures only one call site succeeds, but removing the duplicate here eliminates the redundant warmup await and clarifies the single responsibility: App owns cold-start auto-start, MainActivity owns UI.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < TRIM_MEMORY_UI_HIDDEN || isFinishing) {
            return
        }
        if (!featureStoreReader.exitUiWhenBackground.value) {
            return
        }
        if (proxyFacade.runtimeSnapshot.value.running) {
            finishAndRemoveTask()
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let { safeIntent ->
            if (safeIntent.getBooleanExtra(EXTRA_EXIT_UI_WHEN_BACKGROUND, false)) {
                finishAndRemoveTask()
                return
            }
            extractPendingImportUrl(safeIntent)?.let { com.github.yumelira.yumebox.core.util.PendingImportUrlHolder.set(it) }
            safeIntent.data?.let { uri ->
                val scheme = uri.scheme
                if (scheme == "clash" || scheme == "clashmeta") {
                    val host = uri.host
                    if (host == "install-config") {
                        val configUrl = uri.getQueryParameter("url")
                        if (!configUrl.isNullOrBlank()) {
                            com.github.yumelira.yumebox.core.util.PendingImportUrlHolder.set(configUrl)
                        }
                    }
                } else if (scheme == "yumebox") {
                    _pendingDeepLink.value = uri.toString()
                }
            }
            intentController.handleIntent(safeIntent)
        }
    }

    /**
     * On launch, auto-request the two runtime permissions the app needs: notifications (Android 13+)
     * and the MIUI dynamic "get installed apps" permission. Both are fired in a single system dialog
     * sequence; permissions that aren't runtime-requestable on this device/OS are simply skipped.
     */
    private fun requestStartupPermissions() {
        val permissions =
            buildList {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                if (
                    isMiuiGetInstalledAppsDynamicSupported() &&
                        checkSelfPermission(MIUI_GET_INSTALLED_APPS_PERMISSION) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    add(MIUI_GET_INSTALLED_APPS_PERMISSION)
                }
            }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), REQUEST_STARTUP_PERMISSIONS)
        }
    }

    private fun isMiuiGetInstalledAppsDynamicSupported(): Boolean =
        runCatching {
                packageManager
                    .getPermissionInfo(MIUI_GET_INSTALLED_APPS_PERMISSION, 0)
                    .packageName == "com.lbe.security.miui"
            }
            .getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun applyExcludeFromRecents(exclude: Boolean) {
        runCatching {
            val am = getSystemService(ActivityManager::class.java) ?: return@runCatching
            val currentTaskId = taskId
            val task =
                am.appTasks.firstOrNull { appTask: ActivityManager.AppTask ->
                    val taskInfo = appTask.taskInfo ?: return@firstOrNull false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        taskInfo.taskId == currentTaskId
                    } else {
                        taskInfo.id == currentTaskId
                    }
                }
            task?.setExcludeFromRecents(exclude)
        }
    }
}
