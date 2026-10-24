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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Shizuku / Sui 接入：仅承担两职——「外部启动」的命令执行（shell 身份拉起 priv-kit 服务端）与授权管理；特权操作一律走 priv-kit。Sui 在此处无需显式初始化：`rikka.shizuku.ShizukuProvider`（已在应用清单中声明）会在其 `onCreate` 中完成初始化，而该方法在 `Application.onCreate` 之前运行。 */
object ShizukuManager {
    private const val TAG = "FlyCatShizuku"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val PERMISSION_REQUEST_CODE = 1001
    private const val USER_SERVICE_VERSION = 1
    private const val BIND_TIMEOUT_SECONDS = 3L
    /** 绑定失败后的冷却时长，期间不再重试绑定（外部启动一轮未就绪即放弃），避免频繁 fork user service。 */
    private const val BIND_RETRY_COOLDOWN_MS = 30_000L
    private val userServiceClass: String = PrivilegedServiceImpl::class.java.name
    // 由服务连接回调写入，从外部启动协程的线程读取。
    @Volatile private var privilegedService: IPrivilegedService? = null
    @Volatile private var serviceConnected = false
    @Volatile private var bindLatch = CountDownLatch(1)
    @Volatile private var nextBindAttemptAt = 0L
    @Volatile private var userServiceArgs: Shizuku.UserServiceArgs? = null
    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
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
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
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
    /** 通过 Shizuku user service（shell 身份）执行设备端命令，用于外部拉起 priv-kit 服务端。 */
    internal suspend fun execInUserService(context: Context, commandLine: String): Boolean {
        getPrivilegedService(context)?.let { service ->
            return runCatching { service.exec(commandLine) }
                .onSuccess { ok -> Log.i(TAG, "exec via user service: $ok") }
                .onFailure { error -> Log.w(TAG, "User service exec failed", error) }
                .getOrDefault(false)
        }
        Log.w(TAG, "exec skipped: user service unavailable")
        return false
    }
    private suspend fun getPrivilegedService(context: Context): IPrivilegedService? {
        privilegedService?.takeIf { serviceConnected }?.let { return it }
        // 冷却期内直接放弃本轮绑定，避免重试路径频繁 fork user service 并阻塞等待。
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
