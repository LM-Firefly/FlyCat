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

package com.github.lmfirefly.flycat

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.retainedComponent
import com.github.lmfirefly.flycat.common.util.AppLanguageManager
import com.github.lmfirefly.flycat.core.util.PendingImportUrlHolder
import com.github.lmfirefly.flycat.core.util.path.APPLICATION_SCOPE_NAME
import com.github.lmfirefly.flycat.feature.home.presentation.screen.moe.HomePreviewGuideDialog
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.lmfirefly.flycat.presentation.component.layout.KeyValueEditorScreen
import com.github.lmfirefly.flycat.presentation.navigation.AppNavContainer
import com.github.lmfirefly.flycat.presentation.navigation.AppNavigationComponent
import com.github.lmfirefly.flycat.presentation.theme.FlyTheme
import com.github.lmfirefly.flycat.presentation.theme.ProvideAndroidPlatformTheme
import com.github.lmfirefly.flycat.runtime.client.util.IntentController
import com.github.lmfirefly.flycat.runtime.client.util.extractPendingImportUrl
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.qualifier.named
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : FragmentActivity() {
    companion object {
        private const val REQUEST_STARTUP_PERMISSIONS = 1001
        private const val MIUI_GET_INSTALLED_APPS_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"
        private const val EXTRA_EXIT_UI_WHEN_BACKGROUND = "exit_ui_when_background"
        val pendingImportUrl: StateFlow<String?> = com.github.lmfirefly.flycat.core.util.PendingImportUrlHolder.pendingImportUrl
        fun clearPendingImportUrl() { com.github.lmfirefly.flycat.core.util.PendingImportUrlHolder.clear() }
        private val _pendingDeepLink = MutableStateFlow<String?>(null)
        val pendingDeepLink: StateFlow<String?> = _pendingDeepLink.asStateFlow()
        fun clearPendingDeepLink() { _pendingDeepLink.value = null }
    }
    private val appSettingsReader: com.github.lmfirefly.flycat.core.contract.AppSettingsReader by inject()
    private val appSettingsStore: com.github.lmfirefly.flycat.data.store.AppSettingsStore by inject()
    private val navigationComponent: AppNavigationComponent by lazy {
        retainedComponent { componentContext ->
            AppNavigationComponent(
                componentContext = componentContext,
                predictiveBackEnabledAtLaunch = appSettingsStore.predictiveBackEnabled.value,
            )
        }
    }
    private val featureStoreReader: com.github.lmfirefly.flycat.core.contract.FeatureStoreReader by inject()
    private val networkSettingsStorage: com.github.lmfirefly.flycat.data.store.NetworkSettingsStore by inject()
    private val profilesRepository: com.github.lmfirefly.flycat.runtime.client.ProfilesRepository by inject()
    private val proxyFacade: com.github.lmfirefly.flycat.runtime.client.ProxyFacade by inject()
    private val serviceCache: MMKV by inject(qualifier = named("service_cache"))
    private val applicationScope: CoroutineScope by inject(qualifier = named(APPLICATION_SCOPE_NAME))
    private lateinit var intentController: IntentController
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguageManager.wrap(newBase)) }
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
            // 使用 application scope，使协程能够在 finish() 后仍然存活。
            IntentController(applicationScope, packageName).handleIntent(intent)
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            }
            finish()
            return
        }
        handleIntent(intent)
        requestStartupPermissions()
        val showHomeGuideInitially = savedInstanceState == null && !appSettingsStore.homePreviewGuideShown.value
        if (showHomeGuideInitially) { appSettingsStore.homePreviewGuideShown.set(true) }
        setContent {
            val appSettingsViewModel = koinViewModel<AppSettingsViewModel>()
            val activitySettings by appSettingsViewModel.activitySettings.collectAsStateWithLifecycle()
            LaunchedEffect(activitySettings.excludeFromRecents) { this@MainActivity.applyExcludeFromRecents(activitySettings.excludeFromRecents) }
            ProvideAndroidPlatformTheme {
                val systemDensity = LocalDensity.current
                val scaledDensity = remember(systemDensity, activitySettings.pageScale) { Density(systemDensity.density * activitySettings.pageScale, systemDensity.fontScale) }
                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    FlyTheme(
                        themeMode = activitySettings.themeMode,
                        themeSeedColorArgb = activitySettings.themeSeedColorArgb,
                        invertOnPrimaryColors = activitySettings.invertOnPrimaryColors,
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MiuixTheme.colorScheme.surface,
                        ) {
                            AppNavContainer(navigationComponent)
                            var showHomeGuide by remember { mutableStateOf(showHomeGuideInitially) }
                            // miuix 弹出层（OverlayDialog→DialogLayout）注册进 Scaffold 持有的 dialogStates 渲染；
                            // 此处以透明 Scaffold 作为对话框宿主，不影响触控。
                            Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent) {
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
        // 自动启动完全由 App.scheduleDeferredStartupTasks() 处理，以避免重复调用。
        // AutoStartSessionGate 确保只有一个调用点成功，但在此处删除重复项可以消除多余的预热等待，并明确单一职责：App 负责冷启动时的自动启动，MainActivity 负责 UI。
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < TRIM_MEMORY_UI_HIDDEN || isFinishing) { return }
        if (!featureStoreReader.exitUiWhenBackground.value) { return }
        if (proxyFacade.runtimeSnapshot.value.running) { finishAndRemoveTask() }
    }
    private fun handleIntent(intent: Intent?) {
        intent?.let { safeIntent ->
            if (safeIntent.getBooleanExtra(EXTRA_EXIT_UI_WHEN_BACKGROUND, false)) {
                finishAndRemoveTask()
                return
            }
            extractPendingImportUrl(safeIntent)?.let { com.github.lmfirefly.flycat.core.util.PendingImportUrlHolder.set(it) }
            safeIntent.data?.let { uri ->
                val scheme = uri.scheme
                if (scheme == "clash" || scheme == "clashmeta") {
                    val host = uri.host
                    if (host == "install-config") {
                        val configUrl = uri.getQueryParameter("url")
                        if (!configUrl.isNullOrBlank()) {
                            com.github.lmfirefly.flycat.core.util.PendingImportUrlHolder.set(configUrl)
                        }
                    }
                } else if (scheme == "flycat") {
                    _pendingDeepLink.value = uri.toString()
                }
            }
            intentController.handleIntent(safeIntent)
        }
    }
    /**
     * 启动时，自动请求应用所需的两个运行时权限：通知权限（Android 13+）以及 MIUI 动态"获取已安装应用"权限。
     * 两者会在同一个系统对话框序列中依次弹出；在该设备/系统上不支持运行时请求的权限将被直接跳过。
     */
    private fun requestStartupPermissions() {
        val permissions = buildList {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (
                isMiuiGetInstalledAppsDynamicSupported() && checkSelfPermission(MIUI_GET_INSTALLED_APPS_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                add(MIUI_GET_INSTALLED_APPS_PERMISSION)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), REQUEST_STARTUP_PERMISSIONS)
        }
    }
    private fun isMiuiGetInstalledAppsDynamicSupported(): Boolean = runCatching {
        packageManager.getPermissionInfo(MIUI_GET_INSTALLED_APPS_PERMISSION, 0).packageName == "com.lbe.security.miui"
    }.getOrDefault(false)
    private fun applyExcludeFromRecents(exclude: Boolean) {
        runCatching {
            val am = getSystemService(ActivityManager::class.java) ?: return@runCatching
            val currentTaskId = getTaskId()
            val task = am.appTasks.firstOrNull { appTask: ActivityManager.AppTask ->
                val taskInfo = appTask.taskInfo ?: return@firstOrNull false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    taskInfo.taskId == currentTaskId
                } else {
                    @Suppress("DEPRECATION")
                    taskInfo.id == currentTaskId
                }
            }
            task?.setExcludeFromRecents(exclude)
        }
    }
}
