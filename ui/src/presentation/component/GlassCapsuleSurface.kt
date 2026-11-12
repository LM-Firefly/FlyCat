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
 */

package com.github.lmfirefly.flycat.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.PublishGlassAnchorSurface
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassAnchorSurface
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 与 [GlassTopIconButton] 同材质配方的磨砂胶囊底座：带文字/图标的状态胶囊与圆钮使用。
 *
 * 玻璃采样**兄弟录制层**——位于 GlassScaffold 录制子树内的控件不得采样 LocalTopBarBackdrop
 * （层自引用会让 hwui prepareTree 无限递归崩溃，见特权服务页记录）；
 * 无 RuntimeShader 时回退玻璃面+边缘光，再退 [fallbackColor] 实色。
 *
 * 传入 [anchor] 即可挂接 [GlassPopupHost] 玻璃菜单：胶囊上报边界并隐藏自身、把玻璃材质发布为弹窗
 * 变换的折叠端点（关闭动画落点即胶囊本体，不闪无玻璃样式）；内容区已标注为弹窗拷贝来源，
 * `GlassPopupHost` 的 `anchorContent` 传与 [content] 相同的组合即可。
 */
@Composable
fun GlassCapsuleSurface(
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    fallbackColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    contentAlignment: Alignment = Alignment.Center,
    anchor: GlassPopupAnchor? = null,
    content: @Composable () -> Unit,
) {
    val glass = rememberGlassTopActionStyle()
    val capsuleShape = remember { Capsule() }
    val glassShape = remember(cornerRadius) { GlassShape(cornerRadius) }
    // 无锚点胶囊（如卡内动作按钮）优先采样页面壁纸录制层——否则磨砂玻璃铺在纯色上不可见；
    // 带锚点胶囊仍走自建兄弟层（弹窗变换端点需要自有录制层）。
    val glassBackdrop = if (anchor == null) LocalGlassBackdrop.current else null
    val surfaceBackdrop = rememberSurfaceBackdrop(MiuixTheme.colorScheme.background)
    val sampleBackdrop = glassBackdrop ?: surfaceBackdrop
    if (anchor != null) {
        // 与 GlassTopIconButton 同款 surface 发布：弹窗变换的折叠端点=本胶囊的玻璃材质，
        // 关闭动画落点即胶囊本体（缺发布则端点只剩内容拷贝，会闪一次"无玻璃"样式）。
        val containerMaterial = GlassTopAppBarDefaults.buttonMaterial()
        val containerStroke = GlassTopAppBarDefaults.buttonStroke()
        val anchorSurface = rememberGlassAnchorSurface(surfaceBackdrop, containerMaterial, containerStroke, fallbackColor)
        PublishGlassAnchorSurface(anchor, anchorSurface)
    }
    Box(
        modifier = modifier.then(
            if (anchor != null) {
                Modifier.glassPopupAnchor(anchor = anchor, cornerRadius = cornerRadius, floating = true)
            } else {
                Modifier
            }
        ),
        contentAlignment = contentAlignment,
    ) {
        if (glassBackdrop == null) {
            Box(modifier = Modifier.matchParentSize().layerBackdrop(surfaceBackdrop))
        }
        Box(
            modifier = Modifier.matchParentSize().then(
                if (isRuntimeShaderSupported()) {
                    Modifier.glassPanel(
                        backdrop = sampleBackdrop,
                        shape = glassShape,
                        style = GlassDefaults.Style,
                        material = GlassTopAppBarDefaults.buttonMaterial(),
                        stroke = GlassTopAppBarDefaults.buttonStroke(),
                        shadow = null,
                        shading = false,
                        fallback = Modifier.background(fallbackColor),
                    )
                } else {
                    // 无 RuntimeShader 时按库语义纯色回退（原 FlyBlur.glassSurface 的纹理模糊分支在此不可达，已删）。
                    Modifier.background(fallbackColor, capsuleShape)
                    .edgeStroke(
                        shape = capsuleShape,
                        stroke = GlassTopAppBarDefaults.buttonStroke(),
                    )
                },
            ),
        )
        Box(
            modifier = Modifier.clip(capsuleShape).then(if (anchor != null) Modifier.glassPopupAnchorContent(anchor) else Modifier),
            content = { content() },
        )
    }
}
