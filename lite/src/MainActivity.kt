package com.github.yumelira.yumebox

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.github.yumelira.yumebox.common.util.AppLanguageManager
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.platform.runtime.StartupGate
import com.github.yumelira.yumebox.presentation.navigation.LiteNavContainer
import com.github.yumelira.yumebox.presentation.theme.ProvideAndroidPlatformTheme
import com.github.yumelira.yumebox.presentation.theme.YumeTheme
import com.github.yumelira.yumebox.runtime.client.common.util.extractPendingImportUrl
import com.github.yumelira.yumebox.runtime.client.common.util.IntentController
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.android.ext.android.inject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : FragmentActivity() {
    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001

        private val _pendingImportUrl = MutableStateFlow<String?>(null)
        val pendingImportUrl: StateFlow<String?> = _pendingImportUrl.asStateFlow()

        fun clearPendingImportUrl() {
            _pendingImportUrl.value = null
        }
    }

    private val appSettingsStorage: AppSettingsStore by inject()
    private lateinit var intentController: IntentController

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupGate.loadPrimary()
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)
        intentController = IntentController(lifecycleScope, packageName)
        handleIntent(intent)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION,
            )
        }

        setContent {
            val themeMode by appSettingsStorage.themeMode.state.collectAsStateWithLifecycle()
            val themeSeedColorArgb by appSettingsStorage.themeAccentColorArgb.state.collectAsStateWithLifecycle()
            val invertOnPrimaryColors by appSettingsStorage.invertOnPrimaryColors.state.collectAsStateWithLifecycle()
            val pendingImportValue by pendingImportUrl.collectAsStateWithLifecycle()

            ProvideAndroidPlatformTheme {
                YumeTheme(
                    themeMode = themeMode,
                    themeSeedColorArgb = themeSeedColorArgb,
                    invertOnPrimaryColors = invertOnPrimaryColors,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MiuixTheme.colorScheme.surface,
                    ) {
                        Scaffold {
                            LiteNavContainer(pendingImportUrl = pendingImportValue)
                            if (pendingImportValue != null) clearPendingImportUrl()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val safeIntent = intent ?: return
        extractPendingImportUrl(safeIntent)?.let { _pendingImportUrl.value = it }

        intentController.handleIntent(safeIntent)
    }
}
