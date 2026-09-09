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

package com.github.lmfirefly.flycat.core.util.coroutine

import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Drop-in replacement for `runCatching` that:
 * 1. Re-throws [CancellationException] (prevents coroutine cancellation from being swallowed)
 * 2. Logs unexpected failures via Timber
 *
 * Use this instead of bare `runCatching` in coroutine contexts.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> safeRun(
    tag: String,
    operation: String,
    block: () -> T,
): Result<T> = try {
    Result.success(block())
} catch (error: Exception) {
    if (error is CancellationException) throw error
    Timber.tag(tag).w(error, operation)
    Result.failure(error)
}

/**
 * Like [safeRun] but returns `null` on failure instead of [Result].
 * Replaces `runCatching { ... }.getOrNull()`.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> safeRunOrNull(
    tag: String,
    operation: String,
    block: () -> T,
): T? = try {
    block()
} catch (error: Exception) {
    if (error is CancellationException) throw error
    Timber.tag(tag).w(error, operation)
    null
}

/**
 * Like [safeRun] but returns [default] on failure instead of [Result].
 * Replaces `runCatching { ... }.getOrElse { default }`.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> safeRunOr(
    tag: String,
    operation: String,
    default: T,
    block: () -> T,
): T = try {
    block()
} catch (error: Exception) {
    if (error is CancellationException) throw error
    Timber.tag(tag).w(error, operation)
    default
}

/**
 * Fire-and-forget variant: executes [block], re-throws [CancellationException],
 * and logs other failures at debug level. Use for cleanup, unregister, and
 * other best-effort operations where a failure is expected/acceptable.
 */
@Suppress("TooGenericExceptionCaught")
inline fun safeRunSilent(
    tag: String,
    operation: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        Timber.tag(tag).d(error, operation)
    }
}
