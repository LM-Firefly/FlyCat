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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.ImageResult
import com.github.lmfirefly.flycat.core.util.LocaleUtils
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp

// 国旗加载完成前的占位比例、策略组图标取不到原比例时的回退值，保证两处默认比例一致。
const val IconDefaultAspectRatio = 3f / 2f

// 国旗与策略组图标共用的比例圆角系数，保证两处圆角观感一致。
const val IconCornerRadiusFraction = 0.12f

@Composable
fun CountryFlag(countryCode: String?, modifier: Modifier = Modifier, width: Dp = UiDp.dp20) {
    // 无国家码时不渲染空占位块，避免名称前出现空白块。
    if (countryCode.isNullOrBlank()) return
    val semanticColors = AppTheme.colors
    val flagUrl = remember(countryCode) { LocaleUtils.normalizeFlagUrl(countryCode) }
    val flagLabel = remember(countryCode) { countryCode.trim().ifEmpty { null } ?: LocaleUtils.UNKNOWN_FLAG_CODE }
    val context = LocalContext.current
    val request = remember(context, flagUrl) {
        // 显式尺寸让加载独立于布局，避免尺寸随比例自适应时尺寸解析挂起
        ImageRequest(context, flagUrl) { size(144, 144) }
    }
    val painter = rememberAsyncImagePainter(request = request, alignment = Alignment.Center, contentScale = ContentScale.Fit)
    // 源 SVG 比例不固定，按 intrinsicSize 取原比例（读 state.result 触发加载完成后重组）
    val intrinsic = painter.intrinsicSize
    val aspectRatio = if (painter.state.result is ImageResult.Success && intrinsic.width > 0f && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        IconDefaultAspectRatio
    }
    // 圆角随固定宽度取比例，保证不同图标观感一致。
    val cornerShape = remember(width) { RoundedCornerShape(width * IconCornerRadiusFraction) }
    Box(
        modifier = modifier
            .width(width)
            .height(width / aspectRatio)
            .clip(cornerShape)
            .background(semanticColors.neutralPlaceholderBackground),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = FlyTxt.Component.Flag.ContentDescription.format(flagLabel),
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
