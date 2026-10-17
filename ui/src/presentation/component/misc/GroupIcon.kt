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

package com.github.lmfirefly.flycat.presentation.component.misc

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.ImageResult

private val REGEX_URI_SCHEME = Regex("^[a-zA-Z][a-zA-Z\\d+.-]*:.*$")
private val REGEX_DOMAIN_LIKE = Regex("^[^/\\s]+\\.[^/\\s]+(?:/.*)?$")

/** 补全策略组图标 URL：协议相对、www. 裸域名等写法统一为 https。 */
fun normalizeGroupIconUri(raw: String): String {
    val normalized = raw.trim()
    if (normalized.startsWith("//")) return "https:$normalized"
    if (normalized.startsWith("www.", ignoreCase = true)) return "https://$normalized"
    if (normalized.matches(REGEX_URI_SCHEME)) return normalized
    if (normalized.matches(REGEX_DOMAIN_LIKE)) return "https://$normalized"
    return normalized
}

/** 策略组图标（URL 图源）。仅加载成功才渲染，失败/加载中整块折叠，避免名称前出现占位空白。与 [CountryFlag] 共用比例圆角，观感一致。 */
@Composable
fun GroupIcon(iconUri: String, width: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(context, iconUri) {
        // 显式尺寸：让加载独立于绘制，避免未绘制时 size 永不赋值导致请求挂起
        ImageRequest(context, iconUri) {
            size(144, 144)
            crossfade(true)
        }
    }
    val painter = rememberAsyncImagePainter(request = request)
    if (painter.state.result is ImageResult.Success) {
        val intrinsic = painter.intrinsicSize
        // 固定宽度、高度遵循源图标原始比例自适应；取不到原比例时回退默认比例
        val aspectRatio = if (intrinsic.width > 0f && intrinsic.height > 0f) intrinsic.width / intrinsic.height else IconDefaultAspectRatio
        val cornerShape = remember(width) { RoundedCornerShape(width * IconCornerRadiusFraction) }
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.width(width).height(width / aspectRatio).clip(cornerShape),
        )
    }
}
