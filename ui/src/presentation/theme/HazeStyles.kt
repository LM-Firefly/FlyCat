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

@file:Suppress("ConstPropertyName")

package com.github.yumelira.yumebox.presentation.theme


import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect

object YumeHaze {
    const val ChromeNoiseFactor: Float = 0.06f
    private const val FallbackAlpha: Float = 0.92f

    @Composable
    fun topBarStyle(surface: Color): HazeBlurStyle {
        val material = HazeMaterials.thin(containerColor = surface)
        return material.copy(
            noiseFactor = ChromeNoiseFactor,
            fallbackColorEffect = HazeColorEffect.tint(surface.copy(alpha = FallbackAlpha)),
        )
    }

    @Composable
    fun bottomBarStyle(background: Color): HazeBlurStyle {
        val opacity = AppTheme.opacity
        val material = HazeMaterials.ultraThin(containerColor = background)
        return material.copy(
            backgroundColor = background.copy(alpha = opacity.subtle),
            colorEffects =
                listOf(HazeColorEffect.tint(background.copy(alpha = opacity.softOverlay))),
            noiseFactor = ChromeNoiseFactor,
            fallbackColorEffect = HazeColorEffect.tint(background.copy(alpha = FallbackAlpha)),
        )
    }

    fun sidebarFallbackTint(surface: Color, isDarkSurface: Boolean): HazeColorEffect {
        val alpha = if (isDarkSurface) 0.78f else 0.88f
        return HazeColorEffect.tint(surface.copy(alpha = alpha))
    }

    fun Modifier.chromeEffect(
        state: HazeState?,
        style: HazeBlurStyle?,
        blurRadius: Dp,
        inputScale: HazeInputScale = HazeInputScale.Auto,
        progressive: HazeProgressive? = null,
        noiseFactor: Float = ChromeNoiseFactor,
    ): Modifier {
        if (state == null || style == null) return this
        return hazeEffect(state) {
            this.inputScale = inputScale
            blurEffect {
                this.style = style
                this.blurRadius = blurRadius
                this.noiseFactor = noiseFactor
                if (progressive != null) {
                    this.progressive = progressive
                }
            }
        }
    }

    fun glassColorEffects(
        surface: Color,
        isDarkSurface: Boolean,
    ): List<HazeColorEffect> {
        val glassBase =
            if (isDarkSurface) {
                Color.Black.copy(alpha = 0.24f)
            } else {
                surface.copy(alpha = 0.13f)
            }
        val glassTint = Color.Black.copy(alpha = 0.10f)
        return listOf(
            HazeColorEffect.tint(glassBase),
            HazeColorEffect.tint(glassTint),
        )
    }

    fun glassBackgroundColor(surface: Color, isDarkSurface: Boolean): Color =
        surface.copy(alpha = if (isDarkSurface) 0.18f else 0.10f)
}
