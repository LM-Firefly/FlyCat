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

package com.github.lmfirefly.flycat.core.util

import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * MIUI/HyperOS 的自启动开关。
 *
 * 小米将此开关置于私有 AppOps（10008/10053）中，而非 Android 权限模型，且未暴露任何公开的 getter 或 setter。
 * 未在此处被列入白名单的应用在启动时会被过滤（"进程未被允许自启动"），因此唯一的通用修复方式是引导用户前往拥有该开关的系统设置页面。
 *
 * 状态读取为尽力而为。当 ROM 隐藏该值时，将返回 [State.UNKNOWN]，而非声称开关处于关闭状态。
 */
object XiaomiAutoStart {
    enum class State {
        ENABLED,
        DISABLED,
        UNKNOWN,
    }
    private const val MIUI_OP_AUTO_START = 10008
    private const val MIUI_OP_AUTO_START_NAME = "auto_start"
    private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
    private const val AUTO_START_ACTIVITY = "com.miui.permcenter.autostart.AutoStartManagementActivity"
    private const val OP_AUTO_START_ACTION = "miui.intent.action.OP_AUTO_START"
    private const val EXTRA_PACKAGE_NAME = "extra_pkgname"
    private val xiaomi: Boolean by lazy {
        val names = setOf("xiaomi", "redmi", "poco")
        Build.MANUFACTURER.lowercase() in names ||
            Build.BRAND.lowercase() in names ||
            systemProperty("ro.miui.ui.version.name").isNotEmpty() ||
            systemProperty("ro.mi.os.version.name").isNotEmpty()
    }
    fun isSupported(): Boolean = xiaomi
    fun readState(context: Context): State {
        if (!xiaomi) return State.UNKNOWN
        readMiuiState(context)?.let { return it }
        return readAppOpsState(context)
    }

    /** 打开系统自启动界面。当 ROM 移除了 MIUI 组件时，回退到应用详情页面。仅当所有候选方式都启动失败时才返回 false。 */
    fun openSettings(context: Context): Boolean {
        if (!xiaomi) return false
        val candidates = listOf(
            Intent(OP_AUTO_START_ACTION).apply {
                setPackage(SECURITY_CENTER_PACKAGE)
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(EXTRA_PACKAGE_NAME, context.packageName)
            },
            Intent().setComponent(
                ComponentName(SECURITY_CENTER_PACKAGE, AUTO_START_ACTIVITY)
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
        )
        return candidates.any { context.startActivitySafely(it) }
    }
    private fun readMiuiState(context: Context): State? = runCatching {
        val method = Class.forName("android.miui.AppOpsUtils")
            .getDeclaredMethod(
                "getApplicationAutoStart",
                Context::class.java,
                String::class.java,
            )
        method.isAccessible = true
        when (method.invoke(null, context, context.packageName) as? Int) {
            0 -> State.ENABLED
            1 -> State.DISABLED
            else -> null
        }
    }.getOrNull()

    /** 针对隐藏MIUI辅助类的ROM的后备方案：操作码10008是自启动条目。 */
    private fun readAppOpsState(context: Context): State = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return State.UNKNOWN
        when (checkAutoStartOp(appOps, context.packageName)) {
            AppOpsManager.MODE_ALLOWED -> State.ENABLED
            AppOpsManager.MODE_IGNORED -> State.DISABLED
            else -> State.UNKNOWN
        }
    }.getOrDefault(State.UNKNOWN)

    /** [AppOpsManager.checkOpNoThrow] 的 int 重载方法在公开 SDK 中是隐藏的，因此会先通过反射调用。字符串重载方法是为在 MIUI 构建中以名称（而非数字）注册该操作的情况提供的公开备选方案。 */
    @Suppress("TooGenericExceptionCaught")
    private fun checkAutoStartOp(appOps: AppOpsManager, packageName: String): Int {
        val byNumber = runCatching {
            AppOpsManager::class.java
                .getMethod(
                    "checkOpNoThrow",
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                )
                .invoke(appOps, MIUI_OP_AUTO_START, Process.myUid(), packageName) as Int
        }.getOrNull()
        if (byNumber != null) return byNumber
        return runCatching {
            appOps.checkOpNoThrow(MIUI_OP_AUTO_START_NAME, Process.myUid(), packageName)
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
    }
    private fun Context.startActivitySafely(intent: Intent): Boolean = runCatching {
        if (this !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }.isSuccess
    private fun systemProperty(key: String): String = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as? String
    }.getOrNull().orEmpty()
}
