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

package com.github.yumelira.yumebox.runtime.client.common.util

import android.content.Context
import android.net.VpnService
import com.github.yumelira.yumebox.core.data.AppSettingsReader
import com.github.yumelira.yumebox.core.data.FeatureStoreReader
import com.github.yumelira.yumebox.core.data.NetworkSettingsReader
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.core.util.AutoStartSessionGate
import com.github.yumelira.yumebox.runtime.api.autostart.AutoStartExecutionGate
import com.github.yumelira.yumebox.runtime.api.autostart.AutoStartUpdatePolicy
import com.github.yumelira.yumebox.runtime.client.ProfilesRepository
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import com.github.yumelira.yumebox.runtime.client.RuntimeContractResolver
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CancellationException
import timber.log.Timber

object ProxyAutoStartHelper {
    private const val TAG = "ProxyAutoStartHelper"

    suspend fun checkAndAutoStart(
        context: Context,
        featureStore: FeatureStoreReader,
        proxyFacade: ProxyFacade,
        profilesRepository: ProfilesRepository,
        appSettingsStorage: AppSettingsReader,
        networkSettingsStorage: NetworkSettingsReader,
        serviceCache: MMKV,
    ) {
        RuntimeContractResolver.warmUp(context)
        if (proxyFacade.isRemoteControllerActive()) {
            Timber.tag(TAG).i("Skip auto start: external controller mode active")
            proxyFacade.applyRemoteControllerState()
            return
        }
        if (AutoStartSessionGate.shouldSkipAutoStart()) {
            Timber.tag(TAG).i("Skip auto start: manual pause gate is active in current session")
            return
        }
        if (AutoStartExecutionGate.isExecuting(serviceCache)) {
            Timber.tag(TAG).i("Skip auto start: background auto-restart is still executing")
            return
        }
        val isPostUpdateColdStart = featureStore.consumePostUpdateColdStartPending()

        val activeProfile = try {
            profilesRepository.queryActiveProfile()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Timber.tag(TAG).e(error, "Failed to load active profile")
            return
        }

        tryUpdateActiveProfileOnStart(
            appSettingsStorage = appSettingsStorage,
            profilesRepository = profilesRepository,
            activeProfile = activeProfile,
            isPostUpdateColdStart = isPostUpdateColdStart,
        )

        val automaticRestart = appSettingsStorage.automaticRestart.value
        if (!automaticRestart) {
            return
        }

        if (proxyFacade.runtimeSnapshot.value.running || RuntimeContractResolver.localRuntimeStatus.serviceRunning) {
            return
        }

        if (activeProfile == null) {
            Timber.tag(TAG).w("No active profile for auto start")
            return
        }

        val mode = networkSettingsStorage.proxyMode.value
        if (mode == ProxyMode.Tun && VpnService.prepare(context) != null) {
            Timber.tag(TAG).i("Skip auto start: VPN permission is missing for Tun mode")
            return
        }

        try {
            profilesRepository.setActiveProfile(activeProfile.uuid)
            proxyFacade.startProxy(mode)
            Timber.tag(TAG).i("Auto start ok: profile=${activeProfile.uuid}, mode=$mode")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Timber.tag(TAG).e(error, "Auto start failed: ${error.message}")
        }
    }

    private suspend fun tryUpdateActiveProfileOnStart(
        appSettingsStorage: AppSettingsReader,
        profilesRepository: ProfilesRepository,
        activeProfile: Profile?,
        isPostUpdateColdStart: Boolean,
    ) {
        when (
            AutoStartUpdatePolicy.decide(
                autoUpdateEnabled = appSettingsStorage.autoUpdateCurrentProfileOnStart.value,
                activeProfile = activeProfile,
                skipForPostUpdateColdStart = isPostUpdateColdStart,
            )
        ) {
            AutoStartUpdatePolicy.Decision.Proceed -> Unit
            AutoStartUpdatePolicy.Decision.AutoUpdateDisabled -> return
            AutoStartUpdatePolicy.Decision.SkipPostUpdateColdStart -> {
                Timber.tag(TAG).i("Post-update cold start: skipping profile update, attempting proxy start as fallback")
                return
            }
            AutoStartUpdatePolicy.Decision.NoActiveProfile -> {
                Timber.tag(TAG).d("Skip auto update: no active profile")
                return
            }
            AutoStartUpdatePolicy.Decision.UnsupportedProfileType -> {
                Timber.tag(TAG)
                    .d("Skip auto update: unsupported profile type=${activeProfile?.type}")
                return
            }
            AutoStartUpdatePolicy.Decision.SkipColdStartReason -> return
        }

        val target = activeProfile ?: return
        try {
            profilesRepository.updateProfile(target.uuid)
            Timber.tag(TAG).i("Auto update on start ok: ${target.uuid}")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Timber.tag(TAG).w(error, "Auto update on start failed")
        }
    }
}
