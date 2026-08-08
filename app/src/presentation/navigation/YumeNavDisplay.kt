/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
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
 *
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumeyucca.yumebox.presentation.navigation

import android.graphics.Path
import android.view.animation.PathInterpolator
import androidx.activity.compose.BackHandler
import androidx.collection.mutableObjectFloatMapOf
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.scene.*
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.*
import kotlin.reflect.KClass

private const val PREDICTIVE_BACK_DEAD_ZONE = 0.015f
private const val ETG_CLOSING_SCALE = 0.85f
private const val ETG_ENTERING_SCALE = 0.95f
private const val ETG_CANCEL_MIN_DURATION_MILLIS = 120
private const val ETG_CANCEL_MAX_DURATION_MILLIS = 220
private const val ETG_COMMIT_DURATION_MILLIS = 375
private val EtgGestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private val EtgCancelEasing = Easing { progress -> 1f - (1f - progress).pow(5) }
private val EtgPostCommitInterpolator by lazy {
    PathInterpolator(
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        }
    )
}

private enum class PredictiveBackPhase {
    Idle,
    Gesture,
    Preparing,
    Cancelling,
    Committing,
}

@Stable
internal class YumeNavDisplayState {
    private var phase by mutableStateOf(PredictiveBackPhase.Idle)
    var swipeEdge by mutableStateOf(NavigationEvent.EDGE_NONE)
        private set

    var initialTouchY by mutableStateOf(0f)
        private set

    var latestTouchY by mutableStateOf(0f)
        private set

    var gestureProgress by mutableStateOf(0f)
        private set

    var settleProgress by mutableStateOf(0f)
        private set

    var ignorePredictiveBackUntilIdle by mutableStateOf(false)
        private set

    val isPredictiveBackActive: Boolean
        get() = phase != PredictiveBackPhase.Idle

    val isPredictiveBackGesture: Boolean
        get() = phase == PredictiveBackPhase.Gesture

    val isPredictiveBackPreparing: Boolean
        get() = phase == PredictiveBackPhase.Preparing

    val isPredictiveBackCancelling: Boolean
        get() = phase == PredictiveBackPhase.Cancelling

    val isPredictiveBackCommitting: Boolean
        get() = phase == PredictiveBackPhase.Committing

    val isPredictiveBackSettling: Boolean
        get() = isPredictiveBackCancelling || isPredictiveBackCommitting

    private val canReceivePredictiveBack: Boolean
        get() =
            (phase == PredictiveBackPhase.Idle || phase == PredictiveBackPhase.Gesture) &&
                    !ignorePredictiveBackUntilIdle

    internal fun updatePredictiveBack(
        event: NavigationEvent,
        maxProgress: Float,
    ): Float? {
        if (!canReceivePredictiveBack) return null
        val progress = predictiveBackFraction(event.progress, maxProgress)
        if (phase == PredictiveBackPhase.Idle) {
            phase = PredictiveBackPhase.Gesture
            swipeEdge = event.swipeEdge
            initialTouchY = event.touchY
        }
        if (phase != PredictiveBackPhase.Gesture) return null
        latestTouchY = event.touchY
        gestureProgress = progress
        return progress
    }

    fun cancelPredictiveBack() {
        if (phase == PredictiveBackPhase.Gesture) {
            phase = PredictiveBackPhase.Cancelling
            settleProgress = 0f
        }
    }

    fun completePredictiveBack(): Boolean {
        return !ignorePredictiveBackUntilIdle &&
                when (phase) {
                    PredictiveBackPhase.Idle -> requestProgrammaticBack()
                    PredictiveBackPhase.Gesture -> {
                        phase = PredictiveBackPhase.Committing
                        settleProgress = 0f
                        true
                    }

                    PredictiveBackPhase.Preparing,
                    PredictiveBackPhase.Cancelling,
                    PredictiveBackPhase.Committing -> false
                }
    }

    internal fun requestProgrammaticBack(): Boolean {
        if (phase != PredictiveBackPhase.Idle || ignorePredictiveBackUntilIdle) {
            return phase != PredictiveBackPhase.Idle
        }
        phase = PredictiveBackPhase.Preparing
        swipeEdge = NavigationEvent.EDGE_LEFT
        initialTouchY = 0f
        latestTouchY = 0f
        gestureProgress = 0f
        settleProgress = 0f
        return true
    }

    internal fun commitPreparedBack() {
        if (phase == PredictiveBackPhase.Preparing) {
            phase = PredictiveBackPhase.Committing
            settleProgress = 0f
        }
    }

    internal fun ignorePredictiveBackUntilIdle() {
        ignorePredictiveBackUntilIdle = true
    }

    internal fun onPredictiveBackIdle() {
        ignorePredictiveBackUntilIdle = false
    }

    internal fun updateGestureProgress(progress: Float) {
        gestureProgress = progress.coerceIn(0f, 1f)
    }

    internal fun updateSettleProgress(progress: Float) {
        settleProgress = progress.coerceIn(0f, 1f)
    }

    internal fun finishPredictiveBack() {
        phase = PredictiveBackPhase.Idle
        swipeEdge = NavigationEvent.EDGE_NONE
        initialTouchY = 0f
        latestTouchY = 0f
        gestureProgress = 0f
        settleProgress = 0f
    }
}

@Composable
internal fun rememberYumeNavDisplayState(): YumeNavDisplayState = remember { YumeNavDisplayState() }

/**
 * Navigation3 scene renderer that preserves AndroidX transition behavior while letting YumeBox
 * normalize unreliable OEM predictive-back progress before it reaches SeekableTransitionState.
 */
@Composable
internal fun <T : Any> YumeNavDisplay(
    state: YumeNavDisplayState,
    sceneState: SceneState<T>,
    navigationEventState: NavigationEventState<SceneInfo<T>>,
    maxPredictiveProgress: Float,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform,
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform,
    onBackCommitted: () -> Boolean,
) {
    val scene = sceneState.currentScene
    val latestScene by rememberUpdatedState(scene)
    val latestOnBackCommitted by rememberUpdatedState(onBackCommitted)
    val transitionState = remember { SeekableTransitionState(scene) }
    val transition = rememberTransition(transitionState, label = "YumeNavigationScene")
    val transitionCurrentStateEntries =
        remember(transition.currentState) { sceneState.entries.toList() }
    val previousScene = sceneState.previousScenes.lastOrNull()
    val gesture = navigationEventState.transitionState
    val receivingPredictiveBack =
        !state.isPredictiveBackSettling &&
                !state.ignorePredictiveBackUntilIdle &&
                gesture is InProgress &&
                previousScene != null
    if (state.isPredictiveBackActive && !state.isPredictiveBackGesture && gesture is InProgress) {
        SideEffect { state.ignorePredictiveBackUntilIdle() }
    } else if (gesture !is InProgress && state.ignorePredictiveBackUntilIdle) {
        SideEffect { state.onPredictiveBackIdle() }
    }

    val predictiveHistory = remember {
        object {
            var predictiveClosingKey by mutableStateOf<YumeAnimatedSceneKey?>(null)
            var predictiveEnteringKey by mutableStateOf<YumeAnimatedSceneKey?>(null)
        }
    }
    val isPop =
        isNavigationPop(
            transitionCurrentStateEntries.map { it.contentKey },
            sceneState.entries.map { it.contentKey },
        )
    val predictiveTransitionActive = state.isPredictiveBackActive

    val sceneMap = remember { mutableStateMapOf<YumeAnimatedSceneKey, Scene<T>>() }
    val zIndices = remember { mutableObjectFloatMapOf<YumeAnimatedSceneKey>() }
    val initialKey = YumeAnimatedSceneKey(transition.currentState)
    val targetKey = YumeAnimatedSceneKey(transition.targetState)
    val initialZIndex = zIndices.getOrPut(initialKey) { 0f }
    val targetZIndex =
        when {
            !predictiveTransitionActive &&
                    transition.targetState != scene &&
                    zIndices.containsKey(targetKey) -> zIndices[targetKey]

            initialKey == targetKey -> initialZIndex
            isPop || predictiveTransitionActive -> initialZIndex - 1f
            else -> initialZIndex + 1f
        }
    sceneMap[targetKey] = transition.targetState
    zIndices[targetKey] = targetZIndex

    val sceneToExcludedEntryMap =
        remember(sceneMap.entries.toList(), zIndices.toString()) {
            buildMap {
                val scenes = mutableListOf<Scene<T>>()
                sceneMap.entries
                    .sortedByDescending { zIndices[it.key] }
                    .map { it.value }
                    .forEach { if (!scenes.contains(it)) scenes.add(it) }

                val coveredEntryKeys = mutableSetOf<Any>()
                val shouldSwapExcludedScenesFromTarget =
                    scenes.isNotEmpty() && transition.targetState != scenes.first()
                scenes.forEach { renderedScene ->
                    val newlyCoveredEntryKeys =
                        renderedScene.entries
                            .map { it.contentKey }
                            .filterNot(coveredEntryKeys::contains)
                            .toSet()
                    if (
                        shouldSwapExcludedScenesFromTarget &&
                        transition.targetState != renderedScene
                    ) {
                        put(
                            YumeAnimatedSceneKey(renderedScene),
                            transition.targetState.entries.map { it.contentKey }.toSet(),
                        )
                    } else {
                        put(YumeAnimatedSceneKey(renderedScene), coveredEntryKeys.toMutableSet())
                    }
                    coveredEntryKeys.addAll(newlyCoveredEntryKeys)
                }
                if (shouldSwapExcludedScenesFromTarget) {
                    put(YumeAnimatedSceneKey(transition.targetState), emptySet())
                }
            }
        }

    if (receivingPredictiveBack) {
        val predictiveTarget = requireNotNull(previousScene)
        LaunchedEffect(predictiveTarget, maxPredictiveProgress) {
            snapshotFlow { navigationEventState.transitionState }
                .collect { gestureState ->
                    if (gestureState is InProgress) {
                        val wasActive = state.isPredictiveBackActive
                        val progress =
                            state.updatePredictiveBack(
                                event = gestureState.latestEvent,
                                maxProgress = maxPredictiveProgress,
                            )
                        if (progress != null) {
                            if (!wasActive) {
                                predictiveHistory.predictiveClosingKey = YumeAnimatedSceneKey(scene)
                                predictiveHistory.predictiveEnteringKey =
                                    YumeAnimatedSceneKey(predictiveTarget)
                            }
                            transitionState.seekTo(progress, predictiveTarget)
                        }
                    }
                }
        }
    }

    if (state.isPredictiveBackPreparing && previousScene != null) {
        val predictiveTarget = previousScene
        LaunchedEffect(scene, predictiveTarget) {
            predictiveHistory.predictiveClosingKey = YumeAnimatedSceneKey(scene)
            predictiveHistory.predictiveEnteringKey = YumeAnimatedSceneKey(predictiveTarget)
            transitionState.seekTo(0f, predictiveTarget)
            state.commitPreparedBack()
        }
    }

    if (state.isPredictiveBackSettling) {
        LaunchedEffect(
            predictiveHistory.predictiveClosingKey,
            predictiveHistory.predictiveEnteringKey,
            state.isPredictiveBackCancelling,
            state.isPredictiveBackCommitting,
        ) {
            var completed = false
            try {
                val cancelling = state.isPredictiveBackCancelling
                if (cancelling) {
                    val startProgress = state.gestureProgress
                    animate(
                        initialValue = startProgress,
                        targetValue = 0f,
                        animationSpec =
                            tween(
                                durationMillis =
                                    max(
                                        ETG_CANCEL_MIN_DURATION_MILLIS,
                                        (startProgress * ETG_CANCEL_MAX_DURATION_MILLIS).toInt(),
                                    ),
                                easing = EtgCancelEasing,
                            ),
                    ) { value, _ ->
                        state.updateGestureProgress(value)
                    }
                } else {
                    animate(
                        initialValue = state.settleProgress,
                        targetValue = 1f,
                        animationSpec =
                            tween(
                                durationMillis = ETG_COMMIT_DURATION_MILLIS,
                                easing = LinearEasing,
                            ),
                    ) { value, _ ->
                        state.updateSettleProgress(value)
                    }
                }
                val settledScene =
                    if (cancelling) {
                        latestScene
                    } else {
                        val enteringKey = predictiveHistory.predictiveEnteringKey
                        check(latestOnBackCommitted()) { "Predictive-back pop was not committed" }
                        snapshotFlow { latestScene }
                            .first {
                                enteringKey == null || YumeAnimatedSceneKey(it) == enteringKey
                            }
                    }
                transitionState.snapTo(settledScene)
                completed = true
            } finally {
                if (completed) {
                    state.finishPredictiveBack()
                    predictiveHistory.predictiveClosingKey = null
                    predictiveHistory.predictiveEnteringKey = null
                }
            }
        }
    } else if (!state.isPredictiveBackActive) {
        LaunchedEffect(scene) {
            if (transitionState.currentState != scene) transitionState.animateTo(scene)
        }
    }

    val selectedTransform: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        when {
            predictiveTransitionActive -> EnterTransition.None togetherWith ExitTransition.None
            isPop -> popTransitionSpec()
            else -> transitionSpec()
        }
    }

    transition.AnimatedContent(
        contentKey = { YumeAnimatedSceneKey(it) },
        contentAlignment = contentAlignment,
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        transitionSpec = {
            val transform = selectedTransform()
            ContentTransform(
                targetContentEnter = transform.targetContentEnter,
                initialContentExit = transform.initialContentExit,
                targetContentZIndex = targetZIndex,
                sizeTransform = sizeTransform,
            )
        },
    ) { targetScene ->
        val isSettled = transition.currentState == transition.targetState
        val lifecycleOwner =
            rememberLifecycleOwner(
                maxLifecycle = if (isSettled) Lifecycle.State.RESUMED else Lifecycle.State.STARTED
            )
        val animatedContentScope = remember { this }

        CompositionLocalProvider(
            LocalLifecycleOwner provides lifecycleOwner,
            LocalNavAnimatedContentScope provides animatedContentScope,
            LocalCurrentScene provides targetScene,
            LocalEntriesToExcludeFromCurrentScene provides
                    sceneToExcludedEntryMap.getValue(YumeAnimatedSceneKey(targetScene)),
        ) {
            val animatedSceneKey = YumeAnimatedSceneKey(targetScene)
            val predictiveTransformModifier =
                when (animatedSceneKey) {
                    predictiveHistory.predictiveClosingKey ->
                        Modifier.etgPredictiveBackTransform(
                            state = state,
                            role = EtgPredictiveBackRole.Closing,
                        )

                    predictiveHistory.predictiveEnteringKey ->
                        Modifier.etgPredictiveBackTransform(
                            state = state,
                            role = EtgPredictiveBackRole.Entering,
                        )

                    else -> Modifier
                }
            val blockInputModifier =
                if (predictiveTransitionActive || !isSettled) {
                    remember {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    event.changes.fastForEach { it.consume() }
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(blockInputModifier)
                        .then(predictiveTransformModifier)
                        .background(MiuixTheme.colorScheme.background)
            ) {
                targetScene.content()
            }
        }
    }

    // NavigationEvent handlers inside the entering scene are composed after the host handler.
    // Keep a final, highest-priority guard registered while ETG is settling so a quick second
    // gesture cannot mutate the new route before the first transition has finished.
    val settlingGuardState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = settlingGuardState,
        isBackEnabled = state.isPredictiveBackActive && !state.isPredictiveBackGesture,
        onBackCompleted = {},
    )
    BackHandler(enabled = state.isPredictiveBackActive && !state.isPredictiveBackGesture) {}

    LaunchedEffect(transition) {
        snapshotFlow { transition.isRunning }
            .filter { !it }
            .collect {
                val settledKey = YumeAnimatedSceneKey(transition.targetState)
                sceneMap.keys.toList().forEach { key ->
                    if (key != settledKey) sceneMap.remove(key)
                }
                zIndices.removeIf { key, _ -> key != settledKey }
            }
    }
}

private enum class EtgPredictiveBackRole {
    Closing,
    Entering,
}

private data class EtgPredictiveBackTransform(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    val alpha: Float,
    val cornerRadius: Float,
)

private fun Modifier.etgPredictiveBackTransform(
    state: YumeNavDisplayState,
    role: EtgPredictiveBackRole,
): Modifier = graphicsLayer {
    val transform =
        calculateEtgPredictiveBackTransform(
            state = state,
            role = role,
            width = size.width,
            height = size.height,
            density = density,
        )
    scaleX = transform.scale
    scaleY = transform.scale
    translationX = transform.translationX
    translationY = transform.translationY
    alpha = transform.alpha
    transformOrigin = TransformOrigin.Center
    clip = transform.cornerRadius > 0.5f
    shape = RoundedCornerShape((transform.cornerRadius / max(0.01f, transform.scale) / density).dp)
}

private fun calculateEtgPredictiveBackTransform(
    state: YumeNavDisplayState,
    role: EtgPredictiveBackRole,
    width: Float,
    height: Float,
    density: Float,
): EtgPredictiveBackTransform {
    if (width <= 0f || height <= 0f) {
        return EtgPredictiveBackTransform(1f, 0f, 0f, 1f, 0f)
    }

    val gestureProgress = EtgGestureEasing.transform(state.gestureProgress.coerceIn(0f, 1f))
    val closingScale = lerp(1f, ETG_CLOSING_SCALE, gestureProgress)
    val closingTargetTranslationX =
        if (state.swipeEdge == NavigationEvent.EDGE_LEFT) {
            ((width * (1f - ETG_CLOSING_SCALE)) / 2f) - (8f * density)
        } else {
            0f
        }
    val closingTranslationX = closingTargetTranslationX * gestureProgress
    val verticalRange = max(0f, ((height - (height * closingScale)) / 2f) - (8f * density))
    val touchDelta = state.latestTouchY - state.initialTouchY
    val normalizedTouchDelta = min(1f, abs(touchDelta) / (height / 2f))
    val deceleratedTouchDelta = 1f - (1f - normalizedTouchDelta).pow(2)
    val translationY = touchDelta.sign * deceleratedTouchDelta * verticalRange
    val cornerRadius = 40f * density * gestureProgress

    val enteringTranslationX = -max(width * ETG_ENTERING_SCALE * 0.15f, 96f * density)
    val gestureTransform =
        when (role) {
            EtgPredictiveBackRole.Closing ->
                EtgPredictiveBackTransform(
                    scale = closingScale,
                    translationX = closingTranslationX,
                    translationY = translationY,
                    alpha = 1f,
                    cornerRadius = cornerRadius,
                )

            EtgPredictiveBackRole.Entering ->
                EtgPredictiveBackTransform(
                    scale =
                        lerp(
                            ETG_ENTERING_SCALE,
                            ETG_ENTERING_SCALE * ETG_CLOSING_SCALE,
                            gestureProgress,
                        ),
                    translationX = enteringTranslationX,
                    translationY = translationY,
                    alpha = 1f,
                    cornerRadius = cornerRadius,
                )
        }

    if (!state.isPredictiveBackCommitting) return gestureTransform

    val commitProgress =
        EtgPostCommitInterpolator.getInterpolation(state.settleProgress.coerceIn(0f, 1f))
    val commitCornerRadius = lerp(40f * density, 0f, commitProgress)
    return when (role) {
        EtgPredictiveBackRole.Closing -> {
            val currentLeft =
                ((width - (width * gestureTransform.scale)) / 2f) + gestureTransform.translationX
            EtgPredictiveBackTransform(
                scale = lerp(gestureTransform.scale, 1f, commitProgress),
                translationX =
                    lerp(
                        gestureTransform.translationX,
                        currentLeft + (96f * density),
                        commitProgress,
                    ),
                translationY = lerp(gestureTransform.translationY, 0f, commitProgress),
                alpha = max(1f - (5f * state.settleProgress), 0f),
                cornerRadius = commitCornerRadius,
            )
        }

        EtgPredictiveBackRole.Entering ->
            EtgPredictiveBackTransform(
                scale = lerp(gestureTransform.scale, 1f, commitProgress),
                translationX = lerp(gestureTransform.translationX, 0f, commitProgress),
                translationY = lerp(gestureTransform.translationY, 0f, commitProgress),
                alpha = 1f,
                cornerRadius = commitCornerRadius,
            )
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + ((stop - start) * fraction)

internal fun predictiveBackFraction(rawProgress: Float, maxProgress: Float): Float {
    val normalizedProgress =
        ((rawProgress.coerceIn(0f, 1f) - PREDICTIVE_BACK_DEAD_ZONE) /
                (1f - PREDICTIVE_BACK_DEAD_ZONE))
            .coerceIn(0f, 1f)
    return (normalizedProgress * maxProgress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}

private fun <T : Any> isNavigationPop(oldBackStack: List<T>, newBackStack: List<T>): Boolean {
    if (oldBackStack.isEmpty() || newBackStack.isEmpty()) return false
    if (oldBackStack.first() != newBackStack.first()) return false
    if (newBackStack.size >= oldBackStack.size) return false
    return newBackStack.indices.all { newBackStack[it] == oldBackStack[it] }
}

private data class YumeAnimatedSceneKey(val type: KClass<*>, val key: Any) {
    constructor(scene: Scene<*>) : this(scene::class, scene.key)
}
