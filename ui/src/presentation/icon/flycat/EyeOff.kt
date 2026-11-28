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
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.presentation.icon.FlyCat

val FlyCat.EyeOff: ImageVector
    get() {
        if (eyeOffVector != null) {
            return eyeOffVector!!
        }
        eyeOffVector = ImageVector.Builder(
            name = "EyeOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 26.0f,
            viewportHeight = 26.0f,
        ).apply {
            group(translationX = 1f, translationY = 1f) {
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(10.733f, 5.076f)
                    arcTo(
                        10.744f,
                        10.744f,
                        0f,
                        isMoreThanHalf = false,
                        isPositiveArc = true,
                        21.938f,
                        11.651f,
                    )
                    arcTo(
                        1f,
                        1f,
                        0f,
                        isMoreThanHalf = false,
                        isPositiveArc = true,
                        21.938f,
                        12.347f,
                    )
                    arcTo(
                        10.747f,
                        10.747f,
                        0f,
                        isMoreThanHalf = false,
                        isPositiveArc = true,
                        20.494f,
                        14.837f,
                    )
                }
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(14.084f, 14.158f)
                    arcTo(
                        3f,
                        3f,
                        0f,
                        isMoreThanHalf = true,
                        isPositiveArc = true,
                        9.842f,
                        9.916f,
                    )
                }
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(17.479f, 17.499f)
                    arcTo(
                        10.75f,
                        10.75f,
                        0f,
                        isMoreThanHalf = false,
                        isPositiveArc = true,
                        2.062f,
                        12.348f,
                    )
                    arcTo(
                        1f,
                        1f,
                        0f,
                        isMoreThanHalf = false,
                        isPositiveArc = true,
                        2.062f,
                        11.652f,
                    )
                    arcTo(
                        10.75f,
                        10.75f,
                        0f,
                        isMoreThanHalf = false,
                        isPositiveArc = true,
                        6.508f,
                        6.509f,
                    )
                }
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(2f, 2f)
                    lineTo(22f, 22f)
                }
            }
        }.build()

        return eyeOffVector!!
    }

@Suppress("ObjectPropertyName")
private var eyeOffVector: ImageVector? = null
