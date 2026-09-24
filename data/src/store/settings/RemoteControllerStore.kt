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

package com.github.lmfirefly.flycat.data.store

import com.github.lmfirefly.flycat.core.contract.RemoteControllerStoreReader
import com.github.lmfirefly.flycat.core.model.PausedLocalRuntime
import com.github.lmfirefly.flycat.core.model.RemoteBackend
import com.tencent.mmkv.MMKV

/**
 * 持久化外部控制器模式状态：应用是否应作为纯遥控器运行、已保存的后端列表以及当前活跃的后端。
 *
 * 存储在独立的MMKV文件 (`remote_controller`) 中，使用 [MMKV.MULTI_PROCESS_MODE]，以确保UI进程和服务进程观察到相同的配置。
 *
 * [controllerEnabled] 表示用户偏好设置。应用仅在 [isActive] 为true时才会接管作为远程控制器（偏好设置开启、已选择后端，且该后端当前已连接）。
 */
class RemoteControllerStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv), RemoteControllerStoreReader {
    /** 主开关——开启时（且存在活跃的后端），应用将进入*遥控模式*。 */
    override val controllerEnabled by boolFlow(false)

    /** 仅当会话实际附加到可达的远程后端时，此状态才为真。服务进程通过 [isActive] 读取此状态，以便在首选项已开启但控制器关闭时，不会跳过本地启动。 */
    override val controllerAttached by boolFlow(false)
    override val pausedLocalOwner by strFlow("")
    override val pausedLocalMode by strFlow("")

    /** All saved backends. */
    override val backends by jsonListFlow(
        default = emptyList<RemoteBackend>(),
        decode = { str -> decodeFromString<List<RemoteBackend>>(str) },
        encode = { value -> encodeToString(value) },
    )

    /** Id of the currently active backend, or blank if none selected. */
    override val activeBackendId by strFlow("")

    /** Convenience: resolve the active [RemoteBackend], or null if unset / missing. */
    override fun activeBackend(): RemoteBackend? {
        val id = activeBackendId.value
        if (id.isBlank()) return null
        return backends.value.firstOrNull { it.id == id }
    }

    /**首选项已启用且已选择后端——这并不意味着我们已接管。** */
    override fun isWanted(): Boolean = controllerEnabled.value && activeBackend() != null

    /**当前已连接到可达的远程控制器。*/
    override fun isActive(): Boolean = isWanted() && controllerAttached.value

    override fun rememberPausedLocal(ownerName: String, modeName: String) {
        if (pausedLocalOwner.value.isNotBlank()) return
        pausedLocalOwner.set(ownerName)
        pausedLocalMode.set(modeName)
    }

    override fun takePausedLocal(): PausedLocalRuntime? {
        val ownerName = pausedLocalOwner.value
        val modeName = pausedLocalMode.value
        if (ownerName.isBlank() || modeName.isBlank()) return null
        pausedLocalOwner.set("")
        pausedLocalMode.set("")
        return PausedLocalRuntime(ownerName = ownerName, modeName = modeName)
    }

    companion object {
        private const val MMKV_ID = "remote_controller"

        private val gate by lazy { RemoteControllerStore(MMKVProvider().getMMKV(MMKV_ID)) }

        fun isActive(): Boolean = gate.isActive()
    }
}
