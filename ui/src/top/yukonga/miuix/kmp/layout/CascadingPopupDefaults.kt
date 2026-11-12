// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.layout

import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.anim.folmeSpring

/**
 * 级联弹出菜单的共享默认值。
 *
 * miuix-ui `CascadingPopupDefaults` 的玻璃内联子集（0.9.4 制品无该对象，故在 glass 模块内联）。
 * 保持与上游同名同签名，便于随 glass 模块重新同步。
 */
object CascadingPopupDefaults {

    /** 次级菜单打开后，触发行的箭头旋转到的角度（度）。 */
    fun arrowRotation(layoutDirection: LayoutDirection): Float = if (layoutDirection == LayoutDirection.Ltr) -90f else 90f

    /**
     * 箭头旋转所用的弹簧，收起时拖尾面板。
     *
     * @param expanding 菜单是否正在展开。收起时用更慢的响应。
     */
    fun <T> arrowSpring(expanding: Boolean): SpringSpec<T> = folmeSpring(
        damping = 0.95f,
        response = if (expanding) 0.2f else 0.3f,
    )
}
