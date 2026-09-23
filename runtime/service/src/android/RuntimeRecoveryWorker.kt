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

package com.github.lmfirefly.flycat.runtime.service.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.lmfirefly.flycat.core.contract.ServiceBootstrapHolder
import com.github.lmfirefly.flycat.core.util.coroutine.AutoStartSessionGate
import com.github.lmfirefly.flycat.runtime.service.StatusProvider
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * 周期性兜底自愈：进程在后台死亡后（崩溃/被杀），部分厂商系统不会拉起 START_STICKY 的 TunService，代理就此假死且只能手动进入应用恢复。
 * 该 Worker 每隔一段时间检查运行时状态；若自动重启开启且代理未在运行，则经 [AutoRestartService] 走既有自动启动链路恢复。
 */
class RuntimeRecoveryWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val bootstrap = ServiceBootstrapHolder.reader
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (!bootstrap.automaticRestart) return Result.success()
        if (bootstrap.isRemoteControllerActive()) return Result.success()
        // Doze 模式下跳过——用户正在休眠，唤醒 CPU 仅为空检查不值得。
        if (!force && isDeviceIdle()) return Result.success()
        if (AutoStartSessionGate.shouldSkipAutoStart()) return Result.success()
        // 自动启动链路进行中（冷启动/包替换/其他恢复）不抢跑，避免双链路互踩 TunService 的 startForegroundService 兑现。
        if (bootstrap.isAutoStartInFlight()) return Result.success()
        // 进程刚冷启动时 App 自身的 auto-start 链路会兜底，本周期让行防止竞态；显式恢复（force）除外。
        if (!force && SystemClock.elapsedRealtime() - Process.getStartUptimeMillis() < 2 * 60 * 1000L) return Result.success()
        if (StatusProvider.isTunStarting() || StatusProvider.serviceRunning) return Result.success()
        val intent = Intent(applicationContext, AutoRestartService::class.java).putExtra(AutoRestartService.EXTRA_REASON, AutoRestartService.REASON_RUNTIME_RECOVERY)
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        }.onFailure { error ->
            // Android 12+ 未获电池优化豁免时后台启动前台服务会被拒绝，留待下个周期重试。
            Timber.tag(TAG).w(error, "Runtime recovery kick failed")
        }.isSuccess
        return if (started) {
            Timber.tag(TAG).i("Runtime recovery kick sent")
            Result.success()
        } else {
            Result.retry()
        }
    }
    private fun isDeviceIdle(): Boolean {
        val pm = applicationContext.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && pm?.isDeviceIdleMode == true
    }

    companion object {
        private const val TAG = "RuntimeRecovery"
        private const val WORK_NAME = "runtime_recovery"
        private const val RUN_ONCE_WORK_NAME = "runtime_recovery_once"
        private const val INTERVAL_MINUTES = 15L
        private const val KEY_FORCE = "force"
        /** 显式恢复一次（如覆盖安装后直接拉起被拒）：入队一次性任务，失败时由 WorkManager 退避重试。 */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<RuntimeRecoveryWorker>()
                .setInputData(workDataOf(KEY_FORCE to true))
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(RUN_ONCE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            Timber.tag(TAG).i("Runtime recovery one-shot enqueued")
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RuntimeRecoveryWorker>(
                    repeatInterval = INTERVAL_MINUTES,
                    repeatIntervalTimeUnit = TimeUnit.MINUTES,
                ).addTag(WORK_NAME).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            Timber.tag(TAG).i("Scheduled runtime recovery every ${INTERVAL_MINUTES}min")
        }
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.tag(TAG).i("Runtime recovery scheduling cancelled")
        }
    }
}
