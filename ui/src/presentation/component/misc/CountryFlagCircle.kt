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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.github.lmfirefly.flycat.core.util.LocaleUtils
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp

@Composable
fun CountryFlagCircle(countryCode: String, modifier: Modifier = Modifier, size: Dp = UiDp.dp18) {
    val semanticColors = AppTheme.colors
    val flagUrl = remember(countryCode) { LocaleUtils.normalizeFlagUrl(countryCode) }
    val context = LocalContext.current

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(semanticColors.neutralPlaceholderBackground),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter =
                rememberAsyncImagePainter(
                    request = ImageRequest(context, flagUrl),
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Crop,
                ),
            contentDescription = FlyTxt.Component.Flag.ContentDescription.format(countryCode),
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
