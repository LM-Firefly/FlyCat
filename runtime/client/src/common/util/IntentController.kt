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

import android.content.Intent
import com.github.yumelira.yumebox.core.data.NetworkSettingsReader
import com.github.yumelira.yumebox.core.util.AutoStartSessionGate
import com.github.yumelira.yumebox.runtime.api.service.common.constants.Intents
import com.github.yumelira.yumebox.runtime.client.ProfilesRepository
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

private const val CLASH_SCHEME = "clash"
private const val CLASH_META_SCHEME = "clashmeta"
private const val INSTALL_CONFIG_HOST = "install-config"
private const val INSTALL_CONFIG_QUERY_PARAM = "url"

fun extractPendingImportUrl(intent: Intent?): String? {
    val uri = intent?.data ?: return null
    val scheme = uri.scheme
    if (scheme != CLASH_SCHEME && scheme != CLASH_META_SCHEME) {
        return null
    }
    if (uri.host != INSTALL_CONFIG_HOST) {
        return null
    }
    return uri.getQueryParameter(INSTALL_CONFIG_QUERY_PARAM)?.takeIf { it.isNotBlank() }
}

class IntentController(private val scope: CoroutineScope, private val packageName: String) : KoinComponent {

    private val proxyFacade: ProxyFacade by inject()
    private val profilesRepository: ProfilesRepository by inject()
    private val networkSettingsStorage: NetworkSettingsReader by inject()

    fun handleIntent(intent: Intent?) {
        intent?.let { safeIntent ->
            when (safeIntent.action) {
                Intents.actionStartClash(packageName) -> handleStartClash()
                Intents.actionStopClash(packageName) -> handleStopClash()
                else -> {
                }
            }
        }
    }

    private fun handleStartClash() {
        scope.launch {
            runCatching {
                    val activeProfile = profilesRepository.queryActiveProfile()
                    if (activeProfile == null) {
                        Timber.w("Skip external start: no active profile")
                        return@launch
                    }

                    Timber.i("External start: profile=${activeProfile.name}")
                    AutoStartSessionGate.clearManualPaused()

                    val mode = networkSettingsStorage.proxyMode.value
                    proxyFacade.startProxy(mode)

                    Timber.i("External start ok")
                }
                .onFailure { error -> Timber.e(error, "External start failed") }
        }
    }

    private fun handleStopClash() {
        scope.launch {
            Timber.i("External stop")
            try {
                AutoStartSessionGate.markManualPaused()
                proxyFacade.stopProxy()
                Timber.i("External stop ok")
            } catch (error: Exception) {
                Timber.e(error, "External stop failed")
            }
        }
    }
}
