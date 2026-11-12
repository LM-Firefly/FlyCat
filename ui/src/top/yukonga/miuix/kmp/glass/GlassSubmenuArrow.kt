// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalLayoutDirection
import top.yukonga.miuix.kmp.layout.CascadingPopupDefaults

// FORK（FlyCat）：级联箭头旋转动画公共化（上游 GlassIconDropdownMenu 内联同构逻辑，
// 拟上游化为 CascadingPopupDefaults 级 helper 双侧复用）。

/**
 * 级联行箭头的旋转动画：页面展开时旋转，页面收起时转回。
 * 与 [GlassPopupAnchor.secondaryBackProgress] 衰减相乘可得最终角度。
 */
@Composable
internal fun rememberSubmenuArrowRotation(open: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (open) CascadingPopupDefaults.arrowRotation(LocalLayoutDirection.current) else 0f,
    animationSpec = CascadingPopupDefaults.arrowSpring(open),
    label = "glassSubmenuArrow",
)
