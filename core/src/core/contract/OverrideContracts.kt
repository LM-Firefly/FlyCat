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

import com.github.lmfirefly.flycat.core.model.override.OverrideConfig
import com.github.lmfirefly.flycat.core.model.profile.ProfileBinding
import kotlinx.coroutines.flow.Flow

/** Read/write contract for override config management. Implemented by [data] module. */
interface OverrideConfigRepository {
    suspend fun getUserConfigs(): List<OverrideConfig>
    fun getUserConfigsFlow(): Flow<List<OverrideConfig>>
    suspend fun getById(id: String): OverrideConfig?
    fun getConfigContent(id: String): String?
    fun saveConfigContent(id: String, content: String): Boolean
    suspend fun save(config: OverrideConfig)
    suspend fun delete(id: String): Boolean
    suspend fun duplicate(id: String): OverrideConfig?
    suspend fun reorderUserConfigs(orderedIds: List<String>)
    suspend fun loadCustomRoutingContent(): String?
    suspend fun saveCustomRoutingContent(content: String)
    /** Bundled templates, always listed above user imports. Materializes assets on first read. */
    suspend fun getBuiltInConfigs(): List<OverrideConfig>
}

/** Contract for applying overrides to the active profile. */
interface OverrideApplier {
    suspend fun reapplyActiveProfileOverride(): Boolean
    suspend fun reapplyActiveProfileIfUsingOverride(overrideId: String): Boolean
    suspend fun isActiveProfileUsingOverride(overrideId: String): Boolean
}

/** Read-only contract for profile-override bindings. */
interface ProfileBindingReader {
    fun getAllBindingsFlow(): Flow<List<ProfileBinding>>
    suspend fun getBinding(profileId: String): ProfileBinding?
    suspend fun setBinding(binding: ProfileBinding)
    suspend fun isOverrideInUse(overrideId: String): Boolean
    suspend fun getOverrideUsageCount(overrideId: String): Int
}

/** Contract for applying override configs to a specific profile. */
interface OverrideApplyExecutor {
    suspend fun applyOverride(profileId: String): Boolean
}

/**
 * Ensures default custom routing content exists, healing metadata if needed.
 * Implemented by feature modules that own the preset template data.
 */
interface CustomRoutingInitializer {
    suspend fun ensureDefaultContent(): String
}
