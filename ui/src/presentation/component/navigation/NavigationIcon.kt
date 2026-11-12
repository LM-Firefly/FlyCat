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

package com.github.lmfirefly.flycat.presentation.component.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.github.lmfirefly.flycat.presentation.component.GlassTopAction
import com.github.lmfirefly.flycat.presentation.component.GlassTopIconButton
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.AppTheme.spacing
import com.github.lmfirefly.flycat.presentation.theme.LocalTopBarBackdrop
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.icon.glass.Glass
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun NavigationBackIcon(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
    extraStartPadding: Dp = UiDp.dp24,
    backdrop: LayerBackdrop? = LocalTopBarBackdrop.current,
) {
    NavigationBackIcon(
        onNavigateBack = { navigator.popBackStack() },
        modifier = modifier,
        contentDescription = contentDescription,
        extraStartPadding = extraStartPadding,
        backdrop = backdrop,
    )
}

@Composable
fun NavigationBackIcon(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
    extraStartPadding: Dp = UiDp.dp24,
    backdrop: LayerBackdrop? = LocalTopBarBackdrop.current,
) {
    // 与顶栏右侧动作同款的 42dp 玻璃圆钮；无 backdrop 时自动退化实色 + 边缘光。
    Box(modifier = Modifier.padding(start = extraStartPadding)) {
        GlassTopIconButton(
            action = GlassTopAction(
                icon = MiuixIcons.Glass.Back,
                contentDescription = contentDescription,
                onClick = dropUnlessResumed { onNavigateBack() },
            ),
            backdrop = backdrop,
            modifier = modifier,
        )
    }
}
