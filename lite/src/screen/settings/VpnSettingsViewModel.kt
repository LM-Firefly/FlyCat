package com.github.yumelira.yumebox.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.data.Preference
import com.github.yumelira.yumebox.core.model.AppLanguage
import com.github.yumelira.yumebox.core.model.ThemeMode
import com.github.yumelira.yumebox.core.model.TunStack
import com.github.yumelira.yumebox.data.controller.AppSettingsController
import com.github.yumelira.yumebox.data.controller.NetworkSettingsController
import com.github.yumelira.yumebox.data.store.AppStateManager
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.client.RuntimeStateMapper
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class VpnSettingsViewModel(
    appStateManager: AppStateManager,
    private val appSettingsController: AppSettingsController,
    private val networkSettingsController: NetworkSettingsController,
    private val proxyFacade: ProxyFacade,
) : ViewModel() {
    private val appSettingsStore = appStateManager.appSettingsStore
    private val networkSettingsStore = appStateManager.networkSettingsStore

    val themeMode: Preference<ThemeMode> = appSettingsStore.themeMode
    val invertOnPrimaryColors: Preference<Boolean> = appSettingsStore.invertOnPrimaryColors
    val appLanguage: Preference<AppLanguage> = appSettingsStore.appLanguage
    val dnsHijack: Preference<Boolean> = networkSettingsStore.dnsHijack
    val allowBypass: Preference<Boolean> = networkSettingsStore.allowBypass
    val enableIPv6: Preference<Boolean> = networkSettingsStore.enableIPv6
    val systemProxy: Preference<Boolean> = networkSettingsStore.systemProxy
    val tunStack: Preference<TunStack> = networkSettingsStore.tunStack
    val isRunning: StateFlow<Boolean> =
        proxyFacade.runtimeSnapshot
            .map(RuntimeStateMapper::isActuallyRunning)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                RuntimeStateMapper.isActuallyRunning(proxyFacade.runtimeSnapshot.value),
            )

    fun onDnsHijackChange(enabled: Boolean) {
        networkSettingsController.setAndRestartIfNeeded(dnsHijack, enabled)
    }

    fun onThemeModeChange(mode: ThemeMode) {
        themeMode.set(mode)
    }

    fun onInvertOnPrimaryColorsChange(enabled: Boolean) {
        invertOnPrimaryColors.set(enabled)
    }

    fun onAppLanguageChange(language: AppLanguage) {
        appSettingsController.applyAppLanguage(language)
    }

    fun onAllowBypassChange(enabled: Boolean) {
        networkSettingsController.setAndRestartIfNeeded(allowBypass, enabled)
    }

    fun onEnableIPv6Change(enabled: Boolean) {
        networkSettingsController.setAndRestartIfNeeded(enableIPv6, enabled)
    }

    fun onSystemProxyChange(enabled: Boolean) {
        networkSettingsController.setAndRestartIfNeeded(systemProxy, enabled)
    }

    fun onTunStackChange(stack: TunStack) {
        networkSettingsController.setAndRestartIfNeeded(tunStack, stack)
    }
}
