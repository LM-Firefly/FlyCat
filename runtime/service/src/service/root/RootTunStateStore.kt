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

package com.github.yumelira.yumebox.runtime.service.root

import android.content.Context
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStateStoreContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStateStoreFactoryContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.root.rootTunDecode
import com.github.yumelira.yumebox.runtime.api.service.root.rootTunEncode
import com.github.yumelira.yumebox.runtime.api.service.runtime.entity.RuntimePhase
import com.tencent.mmkv.MMKV

class RootTunStateStore(@Suppress("UNUSED_PARAMETER") context: Context) : RootTunStateStoreContract {
    private val store = MMKV.mmkvWithID(STORE_ID, MMKV.MULTI_PROCESS_MODE)

    @Volatile private var cachedEncoded: String? = null

    @Volatile private var cachedStatus: RootTunStatus? = null

    override fun snapshot(): RootTunStatus {
        val encoded = store.decodeString(KEY_STATUS_JSON)
        if (!encoded.isNullOrBlank()) {
            val lastEncoded = cachedEncoded
            val lastStatus = cachedStatus
            if (lastEncoded != null && lastEncoded == encoded && lastStatus != null) {
                return lastStatus
            }
            return runCatching { rootTunDecode<RootTunStatus>(encoded) }
                .getOrElse { legacySnapshot() }
                .also { decoded ->
                    cachedEncoded = encoded
                    cachedStatus = decoded
                }
        }

        return legacySnapshot().also {
            cachedEncoded = null
            cachedStatus = it
        }
    }

    override fun isRunning(): Boolean = snapshot().running

    override fun updateStatus(status: RootTunStatus) {
        val normalized = status.copy(running = status.state.isActiveOrStopping)
        val encoded = rootTunEncode(normalized)
        store.encode(KEY_STATUS_JSON, encoded)
        store.encode(KEY_RUNNING, normalized.running)
        encodeNullable(KEY_LAST_ERROR, normalized.lastError)
        encodeNullable(KEY_PROFILE_UUID, normalized.profileUuid)
        encodeNullable(KEY_PROFILE_NAME, normalized.profileName)
        cachedEncoded = encoded
        cachedStatus = normalized
    }

    override fun markIdle(error: String?) {
        updateStatus(
            RootTunStatus(
                state = RuntimePhase.Idle,
                lastError = error,
                runtimeReady = false,
                controllerReady = true,
            )
        )
    }

    override fun clear() {
        store.clearAll()
        cachedEncoded = null
        cachedStatus = null
    }

    private fun legacySnapshot(): RootTunStatus {
        val running = store.decodeBool(KEY_RUNNING, false)
        val state = if (running) RuntimePhase.Running else RuntimePhase.Idle
        return RootTunStatus(
            state = state,
            running = state.isActiveOrStopping,
            lastError = store.decodeString(KEY_LAST_ERROR),
            profileUuid = store.decodeString(KEY_PROFILE_UUID),
            profileName = store.decodeString(KEY_PROFILE_NAME),
            runtimeReady = state == RuntimePhase.Running,
            controllerReady = false,
        )
    }

    private fun encodeNullable(key: String, value: String?) {
        if (value.isNullOrBlank()) {
            store.removeValueForKey(key)
        } else {
            store.encode(key, value)
        }
    }

    private companion object {
        const val STORE_ID = "root_tun_state"
        const val KEY_STATUS_JSON = "status_json"
        const val KEY_RUNNING = "running"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_PROFILE_UUID = "profile_uuid"
        const val KEY_PROFILE_NAME = "profile_name"
    }
}

object RootTunStateStoreFactory : RootTunStateStoreFactoryContract {
    override fun create(context: Context): RootTunStateStoreContract {
        return RootTunStateStore(context)
    }
}
