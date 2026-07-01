package com.github.yumelira.yumebox

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.github.yumelira.yumebox.common.util.AppLanguageManager
import com.github.yumelira.yumebox.feature.proxy.ProxySheetContent
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.presentation.theme.ProvideAndroidPlatformTheme
import com.github.yumelira.yumebox.presentation.theme.YumeTheme
import org.koin.android.ext.android.inject

class ProxySheetActivity : ComponentActivity() {
    private val appSettingsStorage: AppSettingsStore by inject()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)

        setContent {
            val themeMode by appSettingsStorage.themeMode.state.collectAsStateWithLifecycle()
            val themeSeedColorArgb by appSettingsStorage.themeAccentColorArgb.state.collectAsStateWithLifecycle()
            val invertOnPrimaryColors by appSettingsStorage.invertOnPrimaryColors.state.collectAsStateWithLifecycle()

            ProvideAndroidPlatformTheme {
                YumeTheme(
                    themeMode = themeMode,
                    themeSeedColorArgb = themeSeedColorArgb,
                    invertOnPrimaryColors = invertOnPrimaryColors,
                ) {
                    ProxySheetContent(
                        onDismiss = {
                            finish()
                            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
                        }
                    )
                }
            }
        }
    }
}
