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

package com.github.lmfirefly.flycat.feature.dashboard.domain

import com.github.lmfirefly.flycat.core.contract.CustomRoutingInitializer
import com.github.lmfirefly.flycat.core.contract.OverrideConfigRepository
import com.github.lmfirefly.flycat.core.model.override.OverrideInternalConstants
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.buildPresetTemplateYaml
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.defaultOverridePresetTemplateSelection

class CustomRoutingBootstrapper(private val overrideConfigRepository: OverrideConfigRepository) : CustomRoutingInitializer {
    override suspend fun ensureDefaultContent(): String {
        val existingContent = overrideConfigRepository.loadCustomRoutingContent()
        if (!existingContent.isNullOrBlank()) {
            // 内容文件存在，但其元数据条目可能缺失（例如内容未经 save() 写入）。
            // 编译管线仅在元数据条目存在时，才会在 profile 链中保留自定义路由覆盖，因此当元数据缺失时，请在此处重新保存以修复元数据。
            val hasMetadata = overrideConfigRepository.getById(
                OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID
            ) != null
            if (!hasMetadata) {
                overrideConfigRepository.saveCustomRoutingContent(existingContent)
            }
            return existingContent
        }
        val generatedYaml = buildPresetTemplateYaml(defaultOverridePresetTemplateSelection())
        overrideConfigRepository.saveCustomRoutingContent(generatedYaml)
        return generatedYaml
    }
}
