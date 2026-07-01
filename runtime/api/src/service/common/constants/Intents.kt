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

package com.github.yumelira.yumebox.runtime.api.service.common.constants

object Intents {
    private fun action(packageName: String, actionName: String): String =
        "$packageName.action.$actionName"

    private fun intentAction(packageName: String, actionName: String): String =
        "$packageName.intent.action.$actionName"

    fun actionProvideUrl(packageName: String): String = action(packageName, "PROVIDE_URL")

    fun actionStartClash(packageName: String): String = action(packageName, "START_CLASH")

    fun actionStopClash(packageName: String): String = action(packageName, "STOP_CLASH")

    fun actionToggleClash(packageName: String): String = action(packageName, "TOGGLE_CLASH")

    fun actionServiceRecreated(packageName: String): String =
        intentAction(packageName, "CLASH_RECREATED")

    fun actionClashStarted(packageName: String): String = intentAction(packageName, "CLASH_STARTED")

    fun actionClashStopped(packageName: String): String = intentAction(packageName, "CLASH_STOPPED")

    fun actionClashRequestStop(packageName: String): String =
        intentAction(packageName, "CLASH_REQUEST_STOP")

    fun actionProfileChanged(packageName: String): String =
        intentAction(packageName, "PROFILE_CHANGED")

    fun actionProfileLoaded(packageName: String): String =
        intentAction(packageName, "PROFILE_LOADED")

    fun actionOverrideChanged(packageName: String): String =
        intentAction(packageName, "OVERRIDE_CHANGED")

    fun actionRootRuntimeFailed(packageName: String): String =
        intentAction(packageName, "ROOT_RUNTIME_FAILED")

    fun actionProxyGroupsUpdated(packageName: String): String =
        intentAction(packageName, "PROXY_GROUPS_UPDATED")

    fun actionAppForeground(packageName: String): String = intentAction(packageName, "APP_FOREGROUND")

    fun actionPatchSelector(packageName: String): String = action(packageName, "PATCH_SELECTOR")

    fun actionPatchOverride(packageName: String): String = action(packageName, "PATCH_OVERRIDE")

    fun actionClearOverride(packageName: String): String = action(packageName, "CLEAR_OVERRIDE")

    fun actionHealthCheck(packageName: String): String = action(packageName, "HEALTH_CHECK")

    fun actionHealthCheckAll(packageName: String): String = action(packageName, "HEALTH_CHECK_ALL")

    const val EXTRA_NAME = "name"
    const val EXTRA_STOP_REASON = "stop_reason"
    const val EXTRA_APP_FOREGROUND = "app_foreground"
    const val EXTRA_UUID = "uuid"
    const val EXTRA_GROUP_NAME = "group_name"
    const val EXTRA_PROXY_NAME = "proxy_name"
    const val EXTRA_PROFILE_ID = "profile_id"
    const val EXTRA_START_PROXY = "start_proxy"
    const val EXTRA_OVERRIDE_SLOT = "override_slot"
    const val EXTRA_OVERRIDE_CONFIG = "override_config"
    const val EXTRA_HEALTH_CHECK_GROUP = "health_check_group"
}
