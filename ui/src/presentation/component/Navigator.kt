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

package com.github.yumelira.yumebox.presentation.component

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey

/**
 * Thin wrapper over the navigation3 back stack ([MutableList] of [NavKey]).
 *
 * Replaces compose-destinations' DestinationsNavigator. The [push]/[pop]/[replaceAll]/[popUntil]
 * methods cover every navigation pattern previously used in FlyCat. The [navigateUp]/[popBackStack]
 * aliases keep call sites that came from the compose-destinations API churn-free.
 */
@Stable
class Navigator(val backStack: MutableList<NavKey>) {
    /** Pushes [key] onto the stack unless it is already on top (mirrors launchSingleTop). */
    fun push(key: NavKey) {
        if (backStack.lastOrNull() != key) {
            backStack.add(key)
        }
    }

    /** Pops the top entry. The root entry is never popped. Returns true if a pop happened. */
    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Replaces the entire stack with [keys]. */
    fun replaceAll(keys: List<NavKey>) {
        backStack.clear()
        backStack.addAll(keys)
    }

    /** Replaces the top entry with [key]. */
    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
        }
        backStack.add(key)
    }

    /** Pops entries until [predicate] is satisfied for the top entry (inclusive of matched stays). */
    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /** Compatibility alias for [pop] (compose-destinations API). */
    fun navigateUp(): Boolean = pop()

    /** Compatibility alias for [pop] (compose-destinations API). */
    fun popBackStack(): Boolean = pop()
}
