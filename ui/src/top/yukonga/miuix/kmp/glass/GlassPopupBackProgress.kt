// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch

/**
 * 预测式返回的进度轨道，带有独立的取消恢复，不干扰弹出层现有的开/关弹簧动画。
 * 手势完成时会把最后一次手势的比例保留到退出结束，避免跳回打开状态。当其他元素需要无缝的视觉衔接时，调用方可以把它保留到下次打开为止。
 */
@Composable
internal fun rememberGlassPopupBackProgress(
    show: Boolean,
    active: Boolean,
    enabled: Boolean,
    retainWhenInactive: Boolean,
    resetSpec: AnimationSpec<Float>,
    onDismissRequest: () -> Unit,
): State<Float> {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(show, active, retainWhenInactive) {
        if (show || (!active && !retainWhenInactive)) progress.snapTo(0f)
    }

    // 玻璃弹出层也要能在没有导航分发器的独立 Box 宿主中工作。
    if (LocalNavigationEventDispatcherOwner.current != null) {
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = backState,
            isBackEnabled = enabled,
            onBackCancelled = { scope.launch { progress.animateTo(0f, resetSpec) } },
            onBackCompleted = onDismissRequest,
        )
        LaunchedEffect(backState) {
            snapshotFlow { backState.transitionState }.collect { state ->
                if (
                    state is NavigationEventTransitionState.InProgress &&
                    state.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                ) {
                    progress.snapTo(state.latestEvent.progress.coerceIn(0f, 1f))
                }
            }
        }
    }
    return progress.asState()
}

/** 保留弹簧动画正常的过冲；仅对外部手势比例做钳制。 */
internal fun popupFractionWithBack(fraction: Float, backProgress: Float): Float = fraction * (1f - backProgress.coerceIn(0f, 1f))
