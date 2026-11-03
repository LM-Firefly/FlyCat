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

package com.github.lmfirefly.flycat.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassAnchorSurface
import top.yukonga.miuix.kmp.glass.GlassDefaults
import top.yukonga.miuix.kmp.glass.GlassMaterial
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassStroke

/**
 * 构建弹窗变换折叠端点所用的玻璃表面：固定 [GlassDefaults.Style]、无衬底材质，
 * 与库 `GlassButtonSurface` 的发布语义一致（药丸/圆钮/胶囊共用）。
 */
@Composable
internal fun rememberGlassAnchorSurface(
    backdrop: Backdrop?,
    material: GlassMaterial,
    stroke: GlassStroke?,
    fill: Color,
): GlassAnchorSurface = remember(backdrop, material, stroke, fill) {
    GlassAnchorSurface(
        backdrop = backdrop,
        style = GlassDefaults.Style,
        material = material,
        underlayMaterial = null,
        stroke = stroke,
        fill = fill,
    )
}

/**
 * 把玻璃表面发布为单个 [GlassPopupAnchor] 的折叠端点：弹窗关闭动画落点即控件本体
 * （缺发布则端点只剩内容拷贝，会闪一次"无玻璃"样式）。销毁时仅清理仍指向本表面的发布。
 */
@Composable
internal fun PublishGlassAnchorSurface(
    anchor: GlassPopupAnchor?,
    surface: GlassAnchorSurface,
    surfaceProgress: State<Float>? = null,
    surfaceOpacity: Float = 1f,
    surfaceFloating: Boolean = true,
) {
    if (anchor == null) return
    DisposableEffect(anchor, surface) {
        onDispose {
            if (anchor.surface === surface) {
                anchor.surface = null
                anchor.surfaceProgress = null
                anchor.surfaceFloating = false
            }
        }
    }
    SideEffect {
        anchor.surface = surface
        anchor.surfaceProgress = surfaceProgress
        anchor.surfaceOpacity = surfaceOpacity
        anchor.surfaceFloating = surfaceFloating
    }
}

/**
 * [PublishGlassAnchorSurface] 的多锚点变体，供药丸这类"一表面多菜单锚点"的容器使用。
 * 每次重组操作列表都会重建，故锚点集合不能作效果键：清理经快照感知持有者读取当前集合。
 */
@Composable
internal fun PublishGlassAnchorSurfaces(
    anchors: List<GlassPopupAnchor>,
    surface: GlassAnchorSurface,
    surfaceProgress: State<Float>? = null,
    surfaceOpacity: Float = 1f,
    surfaceFloating: Boolean = false,
) {
    val currentAnchors = rememberUpdatedState(anchors)
    DisposableEffect(surface) {
        onDispose {
            currentAnchors.value.forEach { anchor ->
                if (anchor.surface === surface) {
                    anchor.surface = null
                    anchor.surfaceProgress = null
                    anchor.surfaceFloating = false
                }
            }
        }
    }
    SideEffect {
        anchors.forEach { anchor ->
            anchor.surface = surface
            anchor.surfaceProgress = surfaceProgress
            anchor.surfaceOpacity = surfaceOpacity
            anchor.surfaceFloating = surfaceFloating
        }
    }
}
