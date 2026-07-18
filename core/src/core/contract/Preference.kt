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

package com.github.lmfirefly.flycat.core.contract

import com.github.lmfirefly.flycat.core.model.AppLanguage
import kotlinx.coroutines.flow.StateFlow

// ═══════════════════════════════════════════════════════════════════════════════
// Language
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Contract for applying a language change at the application level.
 * Implemented by the app module and injected into data-layer controllers.
 */
fun interface LanguageApplier {
    fun apply(language: AppLanguage)
}

data class Preference<T>(val state: StateFlow<T>, private val update: (T) -> Unit, private val get: () -> T, private val refreshState: () -> Unit = { update(get()) }) {
    val value: T
        get() = get()
    fun set(value: T) = update(value)
    fun refresh() = refreshState()
}
fun <T> Preference<List<T>>.add(item: T) = set(value + item)
fun <T> Preference<List<T>>.remove(predicate: (T) -> Boolean) = set(value.filterNot(predicate))
fun <T> Preference<List<T>>.update(predicate: (T) -> Boolean, transform: (T) -> T) = set(value.map { if (predicate(it)) transform(it) else it })
