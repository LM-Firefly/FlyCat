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

package com.github.yumelira.yumebox.data.model

import com.github.yumelira.yumebox.core.model.OverrideInternalConstants

/**
 * APK-bundled override templates (from override-hub). Content lives under
 * `assets/overrides/builtin/` and is materialized into the configs dir on demand so the
 * runtime override chain can resolve a real file path.
 */
data class BuiltInOverrideDefinition(
    val id: String,
    val assetPath: String,
    val contentType: OverrideContentType,
    /** Display name (zh primary; UI may overlay locale later). */
    val name: String,
    val description: String? = null,
)

object BuiltInOverrideCatalog {
    private const val ASSET_DIR = "overrides/builtin"

    val all: List<BuiltInOverrideDefinition> =
        listOf(
            BuiltInOverrideDefinition(
                id = "${OverrideInternalConstants.BUILTIN_OVERRIDE_PREFIX}prevent-dns-leak",
                assetPath = "$ASSET_DIR/prevent_dns_leak.js",
                contentType = OverrideContentType.JavaScript,
                name = "防止 DNS 泄露",
                description = "JavaScript",
            ),
            BuiltInOverrideDefinition(
                id = "${OverrideInternalConstants.BUILTIN_OVERRIDE_PREFIX}add-direct-rules",
                assetPath = "$ASSET_DIR/add_direct_rules.yaml",
                contentType = OverrideContentType.Yaml,
                name = "添加直连规则",
                description = "YAML",
            ),
            BuiltInOverrideDefinition(
                id = "${OverrideInternalConstants.BUILTIN_OVERRIDE_PREFIX}pudding-dog",
                assetPath = "$ASSET_DIR/pudding_dog.yaml",
                contentType = OverrideContentType.Yaml,
                name = "布丁狗的订阅转换",
                description = "YAML",
            ),
            BuiltInOverrideDefinition(
                id = "${OverrideInternalConstants.BUILTIN_OVERRIDE_PREFIX}acl4ssr-online-full",
                assetPath = "$ASSET_DIR/acl4ssr_online_full.yaml",
                contentType = OverrideContentType.Yaml,
                name = "ACL4SSR Online Full",
                description = "YAML",
            ),
        )

    fun find(id: String): BuiltInOverrideDefinition? = all.firstOrNull { it.id == id }

    fun isBuiltIn(id: String): Boolean = OverrideInternalConstants.isBuiltInOverrideId(id)
}
