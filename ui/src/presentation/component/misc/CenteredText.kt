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

package com.github.lmfirefly.flycat.presentation.component.misc

import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import com.github.lmfirefly.flycat.presentation.component.state.EmptyResourceIllustration
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CenteredText(
    firstLine: String,
    secondLine: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    showEmptyResourceIllustration: Boolean = true,
) {
    val spacing = AppTheme.spacing
    val opacity = AppTheme.opacity
    Box(
        modifier = modifier.padding(horizontal = spacing.space24),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = UiDp.dp360).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showEmptyResourceIllustration) {
                EmptyResourceIllustration()
                Spacer(modifier = Modifier.height(spacing.space16))
            }
            Text(
                text = firstLine,
                modifier = Modifier.fillMaxWidth(),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(spacing.space8))
            Text(
                text = secondLine,
                modifier = Modifier.fillMaxWidth(),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = opacity.subtleText),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private class EmojiTextRun(val text: String, val isEmoji: Boolean)

/**
 * emoji 字体度量显著大于正文，混排时 emoji 拖出行盒、观感偏下。
 * 对齐模型（按墨迹，不是 layout box）：
 * - 各段墨迹中心落在同一水平线上，正文段另按 first baseline 共线；
 * - 共享墨迹中心线由正文段的实际布局位置反推（不是各段局部估计的平均）；
 * - 首段为 emoji 时裁掉 advance 左侧留白，避免名称前空白；
 * - 上报 FirstBaseline，便于外层 Row 按基线对齐而不是按盒高。
 */
@Composable
fun EmojiAwareText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = false,
) {
    val runs = remember(text) { splitEmojiRuns(text) }
    if (runs.size <= 1) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap,
            modifier = modifier,
        )
        return
    }
    val density = LocalDensity.current
    val fontSizePx = with(density) { style.fontSize.toPx() }
    val runStyle = remember(style) { style.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)) }
    Layout(
        modifier = modifier,
        content = {
            runs.forEach { run ->
                Text(
                    text = run.text,
                    style = runStyle,
                    color = color,
                    maxLines = 1,
                    overflow = overflow,
                    softWrap = false,
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { m ->
            m.measure(
                constraints.copy(
                    minWidth = 0,
                    maxWidth = Constraints.Infinity,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                )
            )
        }
        // 参考行：正文段盒高 / baseline（同 style 时一致）。
        var textHeight = 0
        var textBaseline = 0
        runs.forEachIndexed { i, run ->
            if (!run.isEmoji) {
                val p = placeables[i]
                if (p.height > textHeight) {
                    textHeight = p.height
                    textBaseline = p[FirstBaseline]
                }
            }
        }
        if (textHeight == 0) {
            textHeight = placeables.maxOfOrNull { it.height } ?: 0
            textBaseline = placeables.firstOrNull()?.get(FirstBaseline) ?: 0
        }
        // 墨迹中心相对各自 Text 布局顶：baseline 用 Compose 实测值（与 includeFontPadding=false 一致），不用 TextPaint.ascent（含 font padding，和布局盒对不上）。
        val inkCenters = FloatArray(placeables.size) { i ->
            inkCenterFromLayoutTop(
                text = runs[i].text,
                fontSizePx = fontSizePx,
                baselineFromTop = placeables[i][FirstBaseline].toFloat(),
                placeableHeight = placeables[i].height,
                isEmoji = runs[i].isEmoji,
            )
        }
        // 共享墨迹中心线：锚定在参考正文段的绝对位置上。
        // 各段 inkCenter 是「相对自身 top」的局部量，不能直接 average 当绝对 Y；要的是同一父布局坐标系里的目标线 T，再令每段 top = T - inkCenter[i]。
        val textRefIndex = runs.indexOfFirst { !it.isEmoji }.takeIf { it >= 0 }
        val targetInkCenter = if (textRefIndex != null) {
            val yText = textBaseline - placeables[textRefIndex][FirstBaseline]
            yText + inkCenters[textRefIndex]
        } else {
            inkCenters[0]
        }
        val ys = IntArray(placeables.size)
        var minTop = 0
        var maxBottom = textHeight
        var totalWidth = 0
        placeables.forEachIndexed { i, p ->
            val y = if (runs[i].isEmoji) {
                (targetInkCenter - inkCenters[i]).roundToInt()
            } else {
                // 正文段 first baseline 共线；同 style 时其墨迹中心也落在 T 上。
                textBaseline - p[FirstBaseline]
            }
            ys[i] = y
            if (y < minTop) minTop = y
            if (y + p.height > maxBottom) maxBottom = y + p.height
            totalWidth += p.width
        }
        // 首段 emoji 的 advance 左留白 → 名称前空白，放置时左移裁掉。
        val leadingPad = if (runs.firstOrNull()?.isEmoji == true) {
            estimateEmojiLeftPad(runs.first().text, fontSizePx)
        } else {
            0f
        }
        // 只包住文字宽度，内容从左侧起排；不要撑满 maxWidth，否则会把同行的 badge/延迟挤走。
        val layoutWidth = (totalWidth - leadingPad).coerceAtLeast(0f)
        val shiftY = -minTop
        val height = (maxBottom - minTop).coerceAtLeast(textHeight)
        layout(layoutWidth.toInt(), height, mapOf(FirstBaseline to (textBaseline + shiftY))) {
            var x = -leadingPad
            placeables.forEachIndexed { i, p ->
                p.place(x = x.toInt(), y = ys[i] + shiftY)
                x += p.width
            }
        }
    }
}

/** 彩色 emoji 的 getTextBounds 需要显式指定字体族；逐个尝试，全空则走几何回退。 */
private val EMOJI_TYPEFACE_NAMES = arrayOf("Noto Color Emoji", "NotoColorEmoji", "sans-serif", "emoji")

/**
 * 段落墨迹中心相对自身 Text 布局顶的距离（px）。
 * [baselineFromTop] 必须是 Compose placeable 的 FirstBaseline（与 includeFontPadding=false 同一坐标系），不能用 TextPaint.ascent——后者含 font padding，会把整段往下推。
 */
private fun inkCenterFromLayoutTop(text: String, fontSizePx: Float, baselineFromTop: Float, placeableHeight: Int, isEmoji: Boolean): Float {
    if (text.isEmpty() || fontSizePx <= 0f) return fontSizePx / 2f
    val baseline = if (baselineFromTop > 0f) baselineFromTop else fontSizePx * 0.8f
    val bounds = Rect()
    val paint = TextPaint().apply { textSize = fontSizePx }
    if (!isEmoji) {
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.height() > 0) {
            return baseline + (bounds.top + bounds.bottom) / 2f
        }
        // CJK/拉丁正文墨迹约在 [baseline - 0.85em, baseline + 0.05em]，中心 ≈ baseline - 0.40em。
        return baseline - fontSizePx * 0.40f
    }
    for (name in EMOJI_TYPEFACE_NAMES) {
        paint.typeface = Typeface.create(name, Typeface.NORMAL)
        bounds.setEmpty()
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.height() > 0) {
            return baseline + (bounds.top + bounds.bottom) / 2f
        }
    }
    // 彩色 emoji 位图近似填满 includeFontPadding=false 的紧盒，用盒中心比固定 em 比例更稳。
    return if (placeableHeight > 0) {
        placeableHeight / 2f
    } else {
        baseline - fontSizePx * 0.32f
    }
}

/** emoji advance 盒的左侧留白（px）。 */
private fun estimateEmojiLeftPad(text: String, fontSizePx: Float): Float {
    if (text.isEmpty() || fontSizePx <= 0f) return 0f
    val paint = TextPaint().apply { textSize = fontSizePx }
    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    if (bounds.width() > 0 && bounds.left > 0) return bounds.left.toFloat()
    val advance = paint.measureText(text)
    val painted = fontSizePx * 0.92f
    return ((advance - painted) / 2f).coerceIn(0f, fontSizePx * 0.35f)
}

private fun splitEmojiRuns(text: String): List<EmojiTextRun> {
    if (text.isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    val runs = mutableListOf<EmojiTextRun>()
    var runStart = iterator.first()
    var runIsEmoji = false
    var start = runStart
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val clusterIsEmoji = isEmojiCluster(text, start, end)
        if (start == runStart) {
            runIsEmoji = clusterIsEmoji
        } else if (clusterIsEmoji != runIsEmoji) {
            runs += EmojiTextRun(text.substring(runStart, start), runIsEmoji)
            runStart = start
            runIsEmoji = clusterIsEmoji
        }
        start = end
        end = iterator.next()
    }
    runs += EmojiTextRun(text.substring(runStart), runIsEmoji)
    return runs
        .filter { it.text.isNotEmpty() }
        .dropWhile { it.text.isBlank() }
        .dropLastWhile { it.text.isBlank() }
}

private fun isEmojiCluster(text: String, start: Int, end: Int): Boolean {
    var i = start
    while (i < end) {
        val cp = text.codePointAt(i)
        if (
            cp == 0xFE0F || cp == 0x200D || cp == 0x20E3 ||
            cp in 0x1F000..0x1FAFF ||
            cp in 0x2300..0x23FF ||
            cp in 0x2600..0x27BF ||
            cp in 0x2B00..0x2BFF
        ) {
            return true
        }
        i += Character.charCount(cp)
    }
    return false
}
