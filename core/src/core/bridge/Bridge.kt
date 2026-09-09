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

package com.github.lmfirefly.flycat.core.bridge

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import com.github.lmfirefly.flycat.core.BuildConfigHolder
import com.github.lmfirefly.flycat.core.Global
import com.github.lmfirefly.flycat.core.kernel.KernelManager
import com.github.lmfirefly.flycat.core.util.NativeLibraryLoader
import com.github.lmfirefly.flycat.core.util.path.runtimeHomeDir
import kotlinx.coroutines.CompletableDeferred
import java.io.File

@Keep
object Bridge {
    /**
     * Triggers native library loading and JNI initialization on the calling thread.
     * Call this from a background thread during app startup so the main thread is never blocked by the synchronous [init] block.
     */
    fun preload() { /* accessing this object triggers init */ }

    external fun nativeCompile(requestJson: String): String

    external fun nativeCompileAndLoadConfigSummary(
        completable: CompletableDeferred<Unit>,
        requestJson: String,
    ): String

    external fun nativeCompileAndInspectGroups(
        requestJson: String,
        profileDir: String,
        excludeNotSelectable: Boolean,
    ): String?

    external fun nativeCompileAndInspectTunRouteExcludeAddress(requestJson: String): String?

    external fun nativeReset()

    external fun nativeForceGc()

    external fun nativeQueryTunnelState(): String

    external fun nativeQueryTrafficNow(): Long

    external fun nativeQueryTrafficTotal(): Long

    external fun nativeQueryConnections(): String

    external fun nativeQueryConnectionsOverview(): String

    external fun nativeQueryConnectionGeneration(): Long

    external fun nativeQueryProxyGroupVersion(): Long

    external fun nativeQueryRules(): String

    external fun nativeSetRuleDisabled(index: Int, disabled: Boolean): Boolean

    external fun nativeCloseConnection(id: String): Boolean

    external fun nativeCloseAllConnections()

    external fun nativeNotifyDnsChanged(dnsList: String)

    external fun nativeNotifyTimeZoneChanged(name: String, offset: Int)

    external fun nativeStartTun(
        fd: Int,
        stack: String,
        gateway: String,
        portal: String,
        dns: String,
        cb: TunInterface,
    )

    external fun nativeStopTun()

    external fun nativeStartRootTun(configYaml: String): String?

    external fun nativeStopRootTun()

    external fun nativeStartHttp(listenAt: String): String?

    external fun nativeStopHttp()

    external fun nativeQueryGroupNames(excludeNotSelectable: Boolean): String

    external fun nativeInspectCompiledGroups(yamlText: String, profileDir: String, excludeNotSelectable: Boolean): String?

    external fun nativeInspectCompiledGroupNames(yamlText: String, excludeNotSelectable: Boolean): String?

    external fun nativeQueryGroup(name: String, sort: String): String?

    external fun nativeQueryGroupsBatch(namesJson: String, sort: String): String?

    external fun nativeHealthCheck(completable: CompletableDeferred<Unit>, name: String)

    external fun nativeHealthCheckProxy(completable: CompletableDeferred<String>, proxyName: String)

    external fun nativeHealthCheckAll()

    external fun nativePatchTunnelMode(mode: String): Boolean

    external fun nativePatchSelector(selector: String, name: String): Boolean

    external fun nativeForcePatchSelector(selector: String, name: String): Boolean

    external fun nativeFetchAndValid(
        completable: FetchCallback,
        path: String,
        url: String,
        force: Boolean,
    )

    external fun nativeQueryProviders(): String

    external fun nativeUpdateProvider(
        completable: CompletableDeferred<Unit>,
        type: String,
        name: String,
    )

    external fun nativeSubscribeLogcat(callback: LogcatInterface)

    external fun nativeUnsubscribeLogcat()

    external fun nativeSubscribeConnectionClose(callback: ConnectionCloseInterface)

    external fun nativeUnsubscribeConnectionClose()

    external fun nativeSubscribeConnectionJoin(callback: ConnectionJoinInterface)

    external fun nativeUnsubscribeConnectionJoin()

    external fun nativeSubscribeTrafficUpdatePacked(callback: TrafficUpdatePackedInterface)

    external fun nativeUnsubscribeTrafficUpdate()

    external fun nativeCoreVersion(): String

    external fun nativeSetCustomUserAgent(userAgent: String)

    external fun nativeSetAgeSecretKey(key: String)

    external fun nativeGenAgeKey(): String?

    external fun nativeAgePublicKey(secretKey: String): String?

    external fun nativeGenX25519KeyPair(): String?

    external fun nativeGenHybridKeyPair(): String?

    external fun nativeVerifySecretKeys(secretKeys: String): Boolean

    external fun nativeToPublicKeys(secretKeys: String): String?

    external fun nativeVerifyPublicKeys(publicKeys: String): Boolean

    external fun nativeConvertMrsToText(filePath: String): String?

    private external fun nativeInit(home: String, versionName: String, sdkVersion: Int, kernelGitVersion: String)

    init {
        val ctx = Global.application

        NativeLibraryLoader.loadCoreLibraries(ctx)

        ParcelFileDescriptor.open(File(ctx.packageCodePath), ParcelFileDescriptor.MODE_READ_ONLY)
            .detachFd()

        val home = ctx.runtimeHomeDir.apply { mkdirs() }.absolutePath
        val versionName =
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
        val sdkVersion = Build.VERSION.SDK_INT

        // 读取活动内核的版本信息，而非使用编译时常量。
        // 这样 web dashboard（/version）会显示实际加载的内核版本。
        val activeKernelVersion = readActiveKernelVersion(ctx)
        nativeInit(home, versionName, sdkVersion, activeKernelVersion)
        if (activeKernelVersion != BuildConfigHolder.kernelGitVersion) {
            BuildConfigHolder.updateKernelVersion(activeKernelVersion)
        }
    }

    /**
     * 从 KernelManager 元数据读取活动内核的版本字符串。
     * 若为下载内核则返回 "Name-shortCommit" 格式（如 "Meta-ac017c"），若为内置内核则返回编译时常量。
     */
    private fun readActiveKernelVersion(ctx: android.content.Context): String {
        val activeId = KernelManager.activeKernelId(ctx)
        if (activeId == KernelManager.BUNDLED_ALPHA_ID) return BuildConfigHolder.kernelGitVersion
        val name = KernelManager.installedName(ctx, activeId)
        val commit = KernelManager.installedCommit(ctx, activeId)
        return if (name.isNotBlank() && !commit.isNullOrBlank()) {
            "${name}-${commit.take(7)}"
        } else {
            BuildConfigHolder.kernelGitVersion
        }
    }
}

@Keep
interface ConnectionCloseInterface {
    fun received(jsonPayload: String)
}

@Keep
interface ConnectionJoinInterface {
    fun received(jsonPayload: String)
}

@Keep
interface TrafficUpdateInterface {
    fun received(jsonPayload: String)
}

@Keep
interface TrafficUpdatePackedInterface {
    fun received(uploadTotal: Long, downloadTotal: Long, uploadSpeed: Long, downloadSpeed: Long)
}
