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

package com.github.lmfirefly.flycat.data.executor

import com.github.lmfirefly.flycat.core.contract.OverrideApplier
import com.github.lmfirefly.flycat.core.model.profile.Profile
import com.github.lmfirefly.flycat.data.store.ProfileBindingProvider
import timber.log.Timber

class ActiveProfileOverrideApplier(private val queryActiveProfile: suspend () -> Profile?, private val bindingProvider: ProfileBindingProvider, private val overrideApplicator: OverrideApplicator) : OverrideApplier {
    override suspend fun reapplyActiveProfileOverride(): Boolean {
        val activeProfile = queryActiveProfile() ?: return true
        val applied = overrideApplicator.applyOverride(activeProfile.uuid.toString())
        if (!applied) { Timber.e("Failed to reapply active profile override: profile=%s", activeProfile.uuid) }
        return applied
    }
    override suspend fun reapplyActiveProfileIfUsingOverride(overrideId: String): Boolean {
        val activeProfile = queryActiveProfile() ?: return true
        val binding = bindingProvider.getBinding(activeProfile.uuid.toString()) ?: return true
        if (!binding.overrideIds.contains(overrideId)) { return true }
        val applied = overrideApplicator.applyOverride(activeProfile.uuid.toString())
        if (!applied) { Timber.e("Failed to reapply active profile override after config change: profile=%s override=%s", activeProfile.uuid, overrideId) }
        return applied
    }
    override suspend fun isActiveProfileUsingOverride(overrideId: String): Boolean {
        val activeProfile = queryActiveProfile() ?: return false
        val binding = bindingProvider.getBinding(activeProfile.uuid.toString()) ?: return false
        return binding.overrideIds.contains(overrideId)
    }
}
