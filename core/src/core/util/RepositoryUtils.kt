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

import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**存储库层的实用函数可减少样板代码。 */
object RepositoryUtils {
    @Suppress("TooGenericExceptionCaught")
    suspend fun <T> safeApiCall(tag: String, operation: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Exception) { // 故障屏障：任何故障都必须成为 Result.failure
        if (error is CancellationException) throw error
        Timber.tag(tag).e(error, "Failed to execute $operation")
        Result.failure(error)
    }
    @Suppress("TooGenericExceptionCaught")
    fun <T> safeCall(tag: String, operation: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Exception) { // 故障屏障：任何故障都必须成为 Result.failure
        if (error is CancellationException) throw error
        Timber.tag(tag).e(error, "Failed to execute $operation")
        Result.failure(error)
    }
}
