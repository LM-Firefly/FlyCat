// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName")

package com.github.lmfirefly.flycat.presentation.component.background

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import androidx.compose.ui.platform.LocalConfiguration
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.floor

/** 是否平板宽度。 */
@Composable
private fun isTabletWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    effectBackground: Boolean = true,
    alpha: () -> Float = { 1f },
) {
    if (!remember { isRuntimeShaderSupported() }) return
    Box(modifier = modifier) {
        val surface = MiuixTheme.colorScheme.surface
        val deviceType = if (isTabletWidth()) DeviceType.PAD else DeviceType.PHONE
        val darkTheme = isDarkTheme()
        val painter = remember { BgEffectPainter() }
        val preset = remember(deviceType, darkTheme) { BgEffectConfig.get(deviceType, darkTheme) }
        val colorStage = remember { Animatable(0f) }
        LaunchedEffect(dynamicBackground, preset) {
            if (!dynamicBackground) return@LaunchedEffect
            var targetStage = floor(colorStage.value) + 1f
            while (isActive) {
                // 拉长配色过渡周期并软化弹簧，让渐变更缓慢地流动。
                delay((preset.colorInterpPeriod * 800).toLong())
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 30f),
                )
                targetStage += 1f
            }
        }
        Spacer(
            modifier = Modifier.fillMaxSize().then(bgModifier).bgEffectDraw(
                painter = painter,
                preset = preset,
                deviceType = deviceType,
                isDarkTheme = darkTheme,
                surface = surface,
                effectBackground = effectBackground,
                isFullSize = isFullSize,
                playing = dynamicBackground,
                colorStage = { colorStage.value },
                alpha = alpha,
            ),
        )
    }
}
