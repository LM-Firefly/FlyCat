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

package com.github.lmfirefly.flycat.runtime.client.util

import android.content.Context
import android.net.VpnService
import com.github.lmfirefly.flycat.core.contract.AppSettingsReader
import com.github.lmfirefly.flycat.core.contract.FeatureStoreReader
import com.github.lmfirefly.flycat.core.contract.NetworkSettingsReader
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.core.model.tunnel.RunMode
import com.github.lmfirefly.flycat.core.util.coroutine.AutoStartSessionGate
import com.github.lmfirefly.flycat.runtime.api.contract.AutoStartExecutionGate
import com.github.lmfirefly.flycat.runtime.api.contract.AutoStartUpdatePolicy
import com.github.lmfirefly.flycat.runtime.client.ProfilesRepository
import com.github.lmfirefly.flycat.runtime.client.ProxyFacade
import com.github.lmfirefly.flycat.runtime.client.RuntimeContractResolver
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CancellationException
import timber.log.Timber

object ProxyAutoStartUtils {
    private const val TAG = "ProxyAutoStartUtils"

    // Fault barrier: auto start is best-effort across IPC/store/native seams; any failure is
    // logged and skipped (CancellationException is rethrown at each catch site).
    @Suppress("TooGenericExceptionCaught")
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

        val activeProfile =
            try {
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

        val mode = networkSettingsStorage.runMode.value
        if (mode == RunMode.VpnService && VpnService.prepare(context) != null) {
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

    // Fault barrier: profile auto update on start is best-effort; failure only logs a warning.
    @Suppress("TooGenericExceptionCaught")
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
                Timber.tag(TAG).d("Skip auto update: post-update cold-start marker consumed")
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
