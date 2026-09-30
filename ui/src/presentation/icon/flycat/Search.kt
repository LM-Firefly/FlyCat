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

package com.github.lmfirefly.flycat.presentation.icon.flycat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.presentation.icon.FlyCat

/** 与 Eye/Speed 同规格的 24dp 描边放大镜（stroke 2 / round cap），避免 miuix 填充图标线宽不一致。 */
val FlyCat.Search: ImageVector
    get() {
        if (searchVector != null) {
            return searchVector!!
        }
        searchVector = ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(11f, 3f)
                arcToRelative(
                    8f,
                    8f,
                    0f,
                    isMoreThanHalf = true,
                    isPositiveArc = true,
                    0f,
                    16f,
                )
                arcToRelative(
                    8f,
                    8f,
                    0f,
                    isMoreThanHalf = true,
                    isPositiveArc = true,
                    0f,
                    -16f,
                )
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(21f, 21f)
                lineToRelative(-4.35f, -4.35f)
            }
        }
        .build()
        return searchVector!!
    }

@Suppress("ObjectPropertyName")
private var searchVector: ImageVector? = null
