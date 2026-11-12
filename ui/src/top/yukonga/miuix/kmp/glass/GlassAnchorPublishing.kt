// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial

// FORK（FlyCat）：锚点表面发布公共化。上游 GlassButtonSurface 内联同款发布逻辑，此处抽为公共 API
// 供按钮与药丸/胶囊容器共用（拟上游化为库 API；多锚点变体为库缺能力）。

/**
 * 构建弹窗变换折叠端点所用的玻璃表面：固定 [GlassDefaults.Style]、无衬底材质，
 * 与 [GlassIconButton] 的发布语义一致（药丸/圆钮/胶囊共用）。
 */
@Composable
internal fun rememberGlassAnchorSurface(
    backdrop: Backdrop?,
    material: GlassMaterial,
    stroke: EdgeStroke?,
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
    surfaceMaterialAlpha: Float = 1f,
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
        // FORK（FlyCat）：采样受限兄弟纯色层的锚点不得把其表面当作折叠端点，否则展开面板里会留下
        // 按钮大小的不透明方块；此类锚点以纯玻璃形态生长（suppressTransformSurface）。
        anchor.surface = if (anchor.suppressTransformSurface) null else surface
        anchor.surfaceProgress = surfaceProgress
        anchor.surfaceMaterialAlpha = surfaceMaterialAlpha
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
    surfaceMaterialAlpha: Float = 1f,
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
            anchor.surface = if (anchor.suppressTransformSurface) null else surface
            anchor.surfaceProgress = surfaceProgress
            anchor.surfaceMaterialAlpha = surfaceMaterialAlpha
            anchor.surfaceFloating = surfaceFloating
        }
    }
}
