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

package com.github.yumelira.yumebox.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class OverrideConfig(
    val id: String,
    val name: String,
    val description: String? = null,
    val contentType: OverrideContentType,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

// ── OverrideContentType ─────────────────────────────────────────────────

@Serializable(with = OverrideContentTypeSerializer::class)
enum class OverrideContentType(val extension: String) {
    @SerialName("yaml") Yaml("yaml"),
    @SerialName("js") JavaScript("js");
    companion object {
        fun fromExtension(extension: String?): OverrideContentType? =
            when (extension?.lowercase()?.removePrefix(".")) {
                "yaml",
                "yml" -> Yaml
                "js" -> JavaScript
                else -> null
            }
        fun fromFileName(fileName: String?): OverrideContentType? = fromExtension(fileName?.substringAfterLast('.', missingDelimiterValue = ""))
    }
}

object OverrideContentTypeSerializer : KSerializer<OverrideContentType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OverrideContentType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OverrideContentType) { encoder.encodeString(value.extension) }
    override fun deserialize(decoder: Decoder): OverrideContentType {
        val raw = decoder.decodeString().trim()
        return when (raw.lowercase()) {
            "yaml", "yml" -> OverrideContentType.Yaml
            "js", "javascript" -> OverrideContentType.JavaScript
            else -> { runCatching { OverrideContentType.valueOf(raw) }.getOrElse { throw SerializationException("Unknown OverrideContentType: $raw") } }
        }
    }
}
