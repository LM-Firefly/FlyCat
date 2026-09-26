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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

// 节奏设计：首行 120ms 响应，步进 56ms 波浪，单行 220ms 与 alpha/位移/缩放同步渐变收尾。
private const val LeadMillis = 120L
private const val StepMillis = 56L
private const val DurationMillis = 220
private const val StaggerLimit = 10
private const val InitialScale = 0.97f
private val Rise = 8.dp
/** 容器弹出类场景（如 WindowBottomSheet 的 folmeSpring(0.9, 0.38) 约 300ms 到位）的揭示起始延迟，使行在容器停稳时衔接浮现。 */
const val SheetLeadMillis = 280L
private fun LazyListState.isAwayFromTop(): Boolean = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

/** [rememberRowReveal] 的“已全部揭示”常量，供非动画调用方作默认参数。 */
val RowRevealAll: State<Int> = object : State<Int> { override val value: Int get() = Int.MAX_VALUE }

/** 行错位揭示计数器：返回允许显示的前导行数（0…[StaggerLimit]，全部揭示后为 [Int.MAX_VALUE]）。每次组合入场时播放一次（[replayKey] 会重新启动它，例如在切换组时）。[listState] 非空且入场时已滚动则跳过动画。 */
@Composable
fun rememberRowReveal(itemCount: Int, replayKey: Any? = null, listState: LazyListState? = null, leadMillis: Long = LeadMillis): State<Int> {
    // 进入组合时列表已滚动（如恢复滚动位置）：跳过入场动画，避免行从视口中部浮现。
    val skipReveal = remember(replayKey) { listState?.isAwayFromTop() == true }
    val revealed = remember(replayKey) { mutableIntStateOf(if (skipReveal) Int.MAX_VALUE else 0) }
    val latestCount = rememberUpdatedState(itemCount)
    LaunchedEffect(replayKey) {
        if (skipReveal) return@LaunchedEffect
        // 在执行交错动画之前先等待内容加载，以避免空列表浪费动画效果。
        val count = snapshotFlow { latestCount.value }.first { it > 0 }
        revealed.intValue = 0
        delay(leadMillis)
        val steps = minOf(count, StaggerLimit)
        for (step in 1..steps) {
            revealed.intValue = step
            if (step < steps) delay(StepMillis)
        }
        revealed.intValue = Int.MAX_VALUE
    }
    return revealed
}

/** 行入场动画驱动：以 State 传递揭示计数并用 [Animatable] 播放，stagger 推进全程零 recompose（计数作参数会触发所有可见行随每步递增重组）。 */
@Composable
fun rememberRowShown(index: Int, revealCount: State<Int>): State<Float> {
    // index >= StaggerLimit 的行不参与波浪，始终保持可见，避免揭示完成时底部行集体闪现。
    val shown = remember { Animatable(if (index >= StaggerLimit || revealCount.value > index) 1f else 0f) }
    LaunchedEffect(index) {
        // 滚动后进入组合的行已揭示，直接显示，不重复播入场动画。
        if (shown.value >= 1f) return@LaunchedEffect
        snapshotFlow { revealCount.value }.first { it > index }
        shown.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = DurationMillis,
                easing = AnimationSpecs.EmphasizedDecelerate,
            ),
        )
    }
    return shown.asState()
}

// 动画值必须以 State 形式在 graphicsLayer block 内读取：拆包读值会让每帧都 recompose 正在动画的行。
@Composable
fun Modifier.rowReveal(shown: State<Float>): Modifier {
    val rise = with(LocalDensity.current) { Rise.toPx() }
    return this.graphicsLayer {
        val value = shown.value
        alpha = value
        val scale = InitialScale + (1f - InitialScale) * value
        scaleX = scale
        scaleY = scale
        translationY = (1f - value) * rise
        // 纯 alpha+transform 无需离屏合成，避免 stagger 期间每行一个离屏 buffer 的合成开销。
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}
