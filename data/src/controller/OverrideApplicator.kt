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

package com.github.lmfirefly.flycat.data.controller

import com.github.lmfirefly.flycat.core.contract.OverrideApplyExecutor
import com.github.lmfirefly.flycat.data.repository.OverrideBindingRepository
import timber.log.Timber

class OverrideApplicator(private val resolver: OverrideBindingRepository, private val onRuntimeOverrideChanged: () -> Unit = {}) : OverrideApplyExecutor {
    override suspend fun applyOverride(profileId: String): Boolean {
        return try {
            val overrideIds = resolver.resolveIds(profileId)
            if (overrideIds.isEmpty()) {
                Timber.d("No overrides bound for profile=%s, notifying runtime to clear", profileId)
                notifyRuntimeOverrideChanged()
                return true
            }
            val missingCount = overrideIds.count { !resolver.exists(it) }
            Timber.i("Apply override chain: profile=%s count=%d missing=%d", profileId, overrideIds.size, missingCount)
            if (missingCount > 0) { Timber.w("Override chain: %d/%d configs missing for profile=%s", missingCount, overrideIds.size, profileId) }
            notifyRuntimeOverrideChanged()
            true
        } catch (error: Exception) { // fault barrier: any resolver/broadcast failure degrades to false
            Timber.e(error, "Failed to apply override for profile: %s", profileId)
            false
        }
    }
    private fun notifyRuntimeOverrideChanged() { onRuntimeOverrideChanged() }
}
