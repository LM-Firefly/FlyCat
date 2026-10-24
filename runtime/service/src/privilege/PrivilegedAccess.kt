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

import android.content.Context
import com.github.lmfirefly.flycat.core.contract.PrivilegeLogListener
import com.github.lmfirefly.flycat.core.contract.PrivilegeStartSource
import com.github.lmfirefly.flycat.core.contract.PrivilegedAccessReader
import com.github.lmfirefly.flycat.runtime.service.notification.HyperIsland

/** 通过核心契约将 priv-kit 特权服务、Shizuku 外部启动通道与 HyperOS 超级岛状态暴露给设置界面。 */
object PrivilegedAccess : PrivilegedAccessReader {
    override val isSuperIslandSupported: Boolean
        get() = HyperIsland.isSupported()
    override fun isShizukuRunning(): Boolean = ShizukuManager.isRunning()
    override fun hasShizukuPermission(): Boolean = ShizukuManager.hasPermission()
    override fun requestShizukuPermission(callback: (Boolean) -> Unit) = ShizukuManager.requestPermission(callback)
    override fun openShizuku(context: Context): Boolean = ShizukuManager.openShizuku(context)
    override val privilegeRunningFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
        get() = PrivilegeManager.running
    override fun isPrivilegeRunning(): Boolean = PrivilegeManager.isRunning()
    override fun startPrivilege(context: Context, source: PrivilegeStartSource, logListener: PrivilegeLogListener?, callback: (Boolean) -> Unit) = PrivilegeManager.start(context, source, logListener, callback)
    override fun pairPrivilege(pairingCode: String, pairingPort: Int, callback: (Boolean) -> Unit) = PrivilegeManager.pair(pairingCode, pairingPort, callback)
    override fun discoverPairingPort(callback: (Int?) -> Unit) = PrivilegeManager.discoverPairingPort(callback)
    override fun startPairingSession(context: Context) = PairingNotification.start(context)
    override fun stopPairingSession(context: Context) = PairingNotification.stop(context)
    override fun stopPrivilege() = PrivilegeManager.stop()
    override fun privilegeServerUid(): Int? = PrivilegeManager.serverUid()
    override fun privilegeManualCommand(): String? = PrivilegeManager.manualCommand()
    override fun privilegeIdentityFingerprint(): String? = PrivilegeManager.identityFingerprint()
    override fun privilegeTcpPort(): Int? = PrivilegeManager.tcpPort()
    override fun configurePrivilegeTcp(enabled: Boolean, callback: (Boolean) -> Unit) = PrivilegeManager.configureTcp(enabled, callback)
}
