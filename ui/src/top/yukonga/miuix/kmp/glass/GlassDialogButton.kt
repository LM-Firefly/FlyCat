// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 对话框操作的视觉角色。 */
enum class GlassDialogButtonRole {
    Normal,
    Primary,
    Destructive,
}

/**
 * 用于 [glassDialogSurface] 之上的对话框操作按钮。
 *
 * 普通和破坏性操作使用半透明色调；主要操作使用主题的主要填充色。按钮不添加背景模糊。
 * 按压反馈同时覆盖在填充色和内容之上。
 *
 * @param onClick 启用的操作被点击时调用。
 * @param modifier 应用于操作的修饰符。
 * @param enabled 操作是否接受输入。
 * @param role 调性、主要或破坏性处理方式。
 * @param cornerRadius 填充、内容裁剪和按压反馈共用的圆角。
 * @param minWidth 操作最小宽度；对话框的操作行通常提供其宽度。
 * @param minHeight 操作最小高度。
 * @param colors 启用和禁用状态的填充色/内容颜色。
 * @param pressedColor 按压时的前景覆盖色。
 * @param insideMargin 操作内容周围的内边距。
 * @param interactionSource 可选的用于观察或驱动交互的交互源。
 * @param content 居中的操作内容。文本请使用 MiuixTheme.textStyles.button。
 */
@Composable
fun GlassDialogButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: GlassDialogButtonRole = GlassDialogButtonRole.Normal,
    cornerRadius: Dp = GlassDialogButtonDefaults.CornerRadius,
    minWidth: Dp = GlassDialogButtonDefaults.MinWidth,
    minHeight: Dp = GlassDialogButtonDefaults.MinHeight,
    colors: ButtonColors = GlassDialogButtonDefaults.buttonColors(role),
    pressedColor: Color = GlassDialogButtonDefaults.pressedColor(),
    insideMargin: PaddingValues = GlassDialogButtonDefaults.InsideMargin,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val indication = remember(pressedColor) { DialogButtonIndication(pressedColor) }
    val containerColor = if (enabled) colors.color else colors.disabledColor
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .clip(shape)
                .background(containerColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = if (enabled) indication else null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
                .padding(insideMargin),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

object GlassDialogButtonDefaults {
    val CornerRadius = 16.dp
    val MinWidth = 0.dp
    val MinHeight = 50.dp
    val InsideMargin = PaddingValues(horizontal = 24.dp)

    /** 当前主题的启用与禁用操作颜色。 */
    @Composable
    fun buttonColors(role: GlassDialogButtonRole = GlassDialogButtonRole.Normal): ButtonColors {
        val scheme = MiuixTheme.colorScheme
        val isDark = scheme.background.luminance() < 0.5f
        val tonal = scheme.onBackground.copy(alpha = if (isDark) 36f / 255f else 15f / 255f)
        val color = if (role == GlassDialogButtonRole.Primary) scheme.primary else tonal
        val disabledColor = if (role == GlassDialogButtonRole.Primary) scheme.primary.copy(alpha = 0.3f) else tonal
        val contentColor = when (role) {
            GlassDialogButtonRole.Normal -> scheme.onBackground.copy(alpha = if (isDark) 0.8f else 0.9f)
            GlassDialogButtonRole.Primary -> scheme.onPrimary
            GlassDialogButtonRole.Destructive -> scheme.error
        }
        val disabledContentColor = when (role) {
            GlassDialogButtonRole.Normal -> scheme.onBackground.copy(alpha = 0.38f)
            GlassDialogButtonRole.Primary -> scheme.onPrimary.copy(alpha = if (isDark) 77f / 255f else 1f)
            GlassDialogButtonRole.Destructive -> scheme.error.copy(alpha = 0.3f)
        }
        return remember(color, disabledColor, contentColor, disabledContentColor) {
            ButtonColors(color, disabledColor, contentColor, disabledContentColor)
        }
    }

    /** 当前主题的按压覆盖色。 */
    @Composable
    fun pressedColor(): Color {
        val scheme = MiuixTheme.colorScheme
        return scheme.onBackground.copy(alpha = if (scheme.background.luminance() < 0.5f) 36f / 255f else 31f / 255f)
    }
}

@Immutable
private data class DialogButtonIndication(val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = DialogButtonIndicationNode(interactionSource, color)
}

private class DialogButtonIndicationNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(),
    DrawModifierNode {
    private val presses = mutableSetOf<PressInteraction.Press>()

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                    else -> return@collect
                }
                invalidateDraw()
            }
        }
    }

    override fun onDetach() {
        presses.clear()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (presses.isNotEmpty()) drawRect(color)
    }
}
