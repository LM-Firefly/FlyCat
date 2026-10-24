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

package com.github.lmfirefly.flycat.runtime.service.privilege

import android.os.IBinder
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** 通过隐藏的连接性 API 添加或删除指定 uid 的 OEM 拒绝防火墙规则；调用方传入连接性服务 binder （priv-kit PrivilegeBinderWrapper——事务以特权身份转发执行）。 */
internal object OemDenyFirewall {
    private const val TAG = "FlyCatFirewall"
    /** `FirewallChain.OEM_DENY`，HyperOS 用于其自有拒绝列表的链。 */
    private const val CHAIN_OEM_DENY = 9
    private const val RULE_ALLOW = 0
    private const val RULE_DENY = 2
    init {
        // 隐藏 API 反射发生在应用进程，先做豁免（release 的加载器启动时已豁免，此处幂等兜底 debug 构建）。
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
            .onFailure { error -> Log.w(TAG, "Unable to exempt hidden APIs", error) }
    }
    fun setPackageDenied(connectivity: IBinder, uid: Int, denied: Boolean): Boolean = runCatching {
        val manager = Class.forName("android.net.IConnectivityManager\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, connectivity)?: error("connectivity service binder is not available")
        manager.javaClass.getMethod("setFirewallChainEnabled", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).invoke(manager, CHAIN_OEM_DENY, true)
        manager.javaClass.getMethod("setUidFirewallRule", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(manager, CHAIN_OEM_DENY, uid, if (denied) RULE_DENY else RULE_ALLOW)
        true
    }.onFailure { error ->
        Log.e(TAG, "Failed to update the OEM deny rule of uid $uid", error)
    }.getOrDefault(false)
}
