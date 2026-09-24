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

package com.github.lmfirefly.flycat.runtime.service.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 访问 Shizuku / Sui 特权服务。Sui 在此处无需显式初始化：`rikka.shizuku.ShizukuProvider`（已在应用清单中声明）会在其 `onCreate` 中完成初始化，而该方法在 `Application.onCreate` 之前运行。 */
object ShizukuManager {
    private const val TAG = "FlyCatShizuku"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val PERMISSION_REQUEST_CODE = 1001
    private const val USER_SERVICE_VERSION = 1
    private const val BIND_TIMEOUT_SECONDS = 3L
    /** 绑定失败后的冷却时长，期间直接走 binder 兜底，避免每轮更新都重试绑定。 */
    private const val BIND_RETRY_COOLDOWN_MS = 30_000L
    /** isAvailable 结果的短 TTL：避免每次岛屿更新都查询 binder 与权限。 */
    private const val AVAILABLE_REFRESH_MS = 10_000L
    /** 打包构建会将此类保留在 APK 的加载器 DEX 中（见 :pack），因为打包后的负载位于 /data/user/0/<pkg> 下，Shizuku 所运行的 shell 用户无法读取。 */
    private const val LOADER_USER_SERVICE_CLASS = "dev.flycat.loader.ShizukuUserService"
    private val userServiceClass: String by lazy {
        runCatching { Class.forName(LOADER_USER_SERVICE_CLASS) }.fold(
            onSuccess = { LOADER_USER_SERVICE_CLASS },
            onFailure = { PrivilegedServiceImpl::class.java.name },
        )
    }
    // 由服务连接回调写入，从流量更新器的线程读取。
    @Volatile private var privilegedService: IPrivilegedService? = null
    @Volatile private var serviceConnected = false
    @Volatile private var bindLatch = CountDownLatch(1)
    @Volatile private var nextBindAttemptAt = 0L
    @Volatile private var userServiceArgs: Shizuku.UserServiceArgs? = null
    private var lastAvailableCheckAt = 0L
    private var lastAvailable = false
    private val bypassMutex = Mutex()
    /** 持有 bypass 的活跃会话数。防火墙仅在 0↔1 翻转，避免每帧改写系统状态。 */
    private var bypassHolders = 0
    /** 计数代表一条尚未恢复的防火墙规则，而非活跃会话。 */
    private var restorePending = false
    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?,
        ) {
            if (binder != null && binder.pingBinder()) {
                privilegedService = IPrivilegedService.Stub.asInterface(binder)
                serviceConnected = true
                bindLatch.countDown()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            resetBinding()
        }
        override fun onBindingDied(name: ComponentName?) {
            resetBinding()
        }
        override fun onNullBinding(name: ComponentName?) {
            resetBinding()
        }
    }
    /** 放弃缓存的 binder 并释放等待中的调用者，而不是让其超时。 */
    private fun resetBinding() {
        privilegedService = null
        serviceConnected = false
        bindLatch.countDown()
    }
    /** 结果短 TTL 缓存（见 [AVAILABLE_REFRESH_MS]）；设置页的实时状态查询走 [hasPermission] 不缓存。 */
    fun isAvailable(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAvailableCheckAt < AVAILABLE_REFRESH_MS) {
            return lastAvailable
        }
        lastAvailableCheckAt = now
        lastAvailable = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return lastAvailable
    }
    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    fun requestPermission(callback: (Boolean) -> Unit) {
        val running = isRunning()
        val granted = running && hasPermission()
        Log.i(TAG, "requestPermission: running=$running granted=$granted")
        if (!running) {
            callback(false)
            return
        }
        if (granted) {
            callback(true)
            return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(
                requestCode: Int,
                grantResult: Int,
            ) {
                Shizuku.removeRequestPermissionResultListener(this)
                Log.i(TAG, "permission result: code=$requestCode grant=$grantResult")
                callback(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }.onSuccess { Log.i(TAG, "requestPermission: request sent, awaiting result") }.onFailure { error ->
            Log.w(TAG, "Shizuku permission request failed", error)
            Shizuku.removeRequestPermissionResultListener(listener)
            callback(false)
        }
    }
    fun openShizuku(context: Context): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?: return@runCatching false
        context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
    /** 取得一份 XMSF bypass 持有。HyperOS 只在网络持续被阻断时保留非官方超级岛，故防火墙在首个持有者到达时阻断、最后一个持有者离开时恢复。返回 false 表示防火墙未能改动，稍后调用会重试。 */
    suspend fun acquireXmsfBypass(context: Context): Boolean = bypassMutex.withLock {
        if (restorePending) {
            restorePending = false
            return@withLock true
        }
        if (bypassHolders > 0) {
            bypassHolders += 1
            return@withLock true
        }
        if (!isAvailable()) {
            Log.i(TAG, "island bypass skipped: available=false")
            return@withLock false
        }
        if (!setXmsfNetworkingBlocked(context, blocked = true)) return@withLock false
        bypassHolders = 1
        true
    }
    /** 释放一份持有。若本持有仍是最后一份且防火墙恢复失败，计数保持为一以便下次释放重试。 */
    suspend fun releaseXmsfBypass(context: Context): Boolean = bypassMutex.withLock {
        when {
            bypassHolders > 1 -> {
                bypassHolders -= 1
                true
            }
            bypassHolders == 1 && setXmsfNetworkingBlocked(context, blocked = false) -> {
                bypassHolders = 0
                restorePending = false
                true
            }
            bypassHolders == 1 -> {
                restorePending = true
                false
            }
            else -> true
        }
    }
    private suspend fun setXmsfNetworkingBlocked(context: Context, blocked: Boolean): Boolean {
        val xmsfUid = runCatching { context.packageManager.getPackageUid(XMSF_PACKAGE, 0) }
                .onFailure { error -> Log.w(TAG, "$XMSF_PACKAGE is not installed", error) }
                .getOrNull()
                ?: return false
        getPrivilegedService(context)?.let { service ->
            return runCatching { service.setPackageNetworkingEnabled(xmsfUid, !blocked) }
                .onSuccess { ok -> Log.i(TAG, "xmsf networking ${if (blocked) "blocked" else "restored"} via user service: $ok") }
                .onFailure { error -> Log.w(TAG, "User service call failed", error) }
                .getOrDefault(false)
        }
        // 当用户服务无法绑定时的回退方案：通过 Shizuku binder 直接驱动连接服务，该 binder 以相同的特权身份运行。
        val connectivity = SystemServiceHelper.getSystemService("connectivity") ?: return false
        return OemDenyFirewall.setPackageDenied(
            connectivity = ShizukuBinderWrapper(connectivity),
            uid = xmsfUid,
            denied = blocked,
        ).also { ok -> Log.i(TAG, "xmsf networking ${if (blocked) "blocked" else "restored"} via binder fallback: $ok") }
    }
    private suspend fun getPrivilegedService(context: Context): IPrivilegedService? {
        privilegedService?.takeIf { serviceConnected }?.let { return it }
        // 冷却期内直接走 binder 兵底，避免每轮更新都 fork user service 并阻塞等待。
        if (SystemClock.elapsedRealtime() < nextBindAttemptAt) {
            return null
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                bindLatch = CountDownLatch(1)
                val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, userServiceClass)).daemon(false).processNameSuffix("privileged").debuggable(false).version(USER_SERVICE_VERSION)
                userServiceArgs = args
                Shizuku.bindUserService(args, serviceConnection)
                if (bindLatch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    nextBindAttemptAt = 0L
                    privilegedService
                } else {
                    Log.w(TAG, "Timed out binding the Shizuku user service")
                    nextBindAttemptAt = SystemClock.elapsedRealtime() + BIND_RETRY_COOLDOWN_MS
                    null
                }
            }
            .onFailure { error ->
                Log.w(TAG, "Unable to bind the Shizuku user service", error)
                nextBindAttemptAt = SystemClock.elapsedRealtime() + BIND_RETRY_COOLDOWN_MS
            }
            .getOrNull()
        }
    }
    /** 服务停止时释放常驻的 user service 进程。 */
    fun unbindUserService() {
        val args = userServiceArgs ?: return
        userServiceArgs = null
        runCatching { Shizuku.unbindUserService(args, serviceConnection, true) }
            .onFailure { error -> Log.w(TAG, "Unable to unbind the Shizuku user service", error) }
        resetBinding()
    }
}
