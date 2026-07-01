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

package com.github.yumelira.yumebox.data.store

import com.github.yumelira.yumebox.core.data.ProfileLinksReader
import com.github.yumelira.yumebox.core.model.LinkOpenMode
import com.github.yumelira.yumebox.core.model.ProfileLink
import com.tencent.mmkv.MMKV
import kotlinx.serialization.json.Json

class ProfileLinksStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv), ProfileLinksReader {
    private val json = Json { ignoreUnknownKeys = true }

    override val linkOpenMode by enumFlow(LinkOpenMode.IN_APP)

    override val links by
        jsonListFlow(
            default = emptyList(),
            decode = { str -> decodeFromString<List<ProfileLink>>(str) },
            encode = { value -> encodeToString(value) },
        )

    override val defaultLinkId by strFlow(default = "")
}
