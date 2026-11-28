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

package com.github.lmfirefly.flycat.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.github.lmfirefly.flycat.core.model.ThemeMode
import com.github.lmfirefly.flycat.locale.ProvideFlyStrings
import top.yukonga.miuix.kmp.material.sensor.DeviceTilt
import top.yukonga.miuix.kmp.material.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val LocalPlatformSystemUiEffect = compositionLocalOf<@Composable () -> Unit> { {} }
// 每个 FlyTheme 子树共享一个姿态监听，避免列表项为描边重复注册传感器。
internal val LocalDeviceTilt = staticCompositionLocalOf<State<DeviceTilt>> { mutableStateOf(DeviceTilt.Zero) }

@Composable
fun FlyTheme(
    themeMode: ThemeMode? = null,
    themeSeedColorArgb: Long = DEFAULT_THEME_SEED_ARGB,
    invertOnPrimaryColors: Boolean = false,
    spacing: Spacing = Spacing(),
    radii: Radii = Radii(),
    sizes: Sizes = Sizes(),
    opacity: Opacity = Opacity(),
    appColors: AppColors = AppColors(),
    content: @Composable () -> Unit,
) = ProvideFlyStrings {
    LocalPlatformSystemUiEffect.current()
    val effectiveThemeMode = themeMode ?: ThemeMode.Auto
    val isDark = when (effectiveThemeMode) {
        ThemeMode.Auto -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colorScheme = remember(isDark, themeSeedColorArgb, invertOnPrimaryColors) {
        colorSchemeFromSeed(
            seed = colorFromArgb(themeSeedColorArgb),
            isDark = isDark,
            invertOnPrimaryColors = invertOnPrimaryColors,
        )
    }

    val deviceTilt = rememberDeviceTilt()
    CompositionLocalProvider(
        LocalSpacing provides spacing,
        LocalRadii provides radii,
        LocalSizes provides sizes,
        LocalOpacity provides opacity,
        LocalAppColors provides appColors,
        LocalDeviceTilt provides deviceTilt,
    ) {
        MiuixTheme(colors = colorScheme) { content() }
    }
}
