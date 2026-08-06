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

@file:Suppress("DuplicatedCode", "FunctionName")

package com.github.yumeyucca.yumebox.presentation.navigation


import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.yumeyucca.yumebox.MainScreen
import com.github.yumeyucca.yumebox.presentation.component.LocalNavigator
import com.github.yumeyucca.yumebox.presentation.component.Navigator
import com.github.yumeyucca.yumebox.screen.settings.MoeWallpaperCropScreen

private const val DURATION = 340
private const val FADE_DURATION = 140
private const val PAGE_SCALE = 0.94f
private val slideEasing = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.0f)

/**
 * A full-width page push with a subtle depth scale.
 *
 * The entering page travels in from the right while expanding to its final size. At the same time,
 * the current page is pushed fully to the left and scales down. This preserves the app's page-push
 * navigation while making the hand-off feel layered.
 */
private fun pushScaleEnter(
    offset: (Int) -> Int,
    transformOrigin: TransformOrigin,
): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(DURATION, easing = slideEasing),
        initialOffsetX = offset,
    ) +
        scaleIn(
            initialScale = PAGE_SCALE,
            transformOrigin = transformOrigin,
            animationSpec = tween(DURATION, easing = slideEasing),
        ) +
        fadeIn(animationSpec = tween(FADE_DURATION, easing = LinearEasing))

private fun pushScaleExit(
    offset: (Int) -> Int,
    transformOrigin: TransformOrigin,
): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(DURATION, easing = slideEasing),
        targetOffsetX = offset,
    ) +
        scaleOut(
            targetScale = PAGE_SCALE,
            transformOrigin = transformOrigin,
            animationSpec = tween(DURATION, easing = slideEasing),
        ) +
        fadeOut(animationSpec = tween(FADE_DURATION, easing = LinearEasing))

/**
 * The app's navigation3 host. Renders the back stack through [NavDisplay] using YumeBox's original
 * horizontal push + scale transitions. The system predictive-back gesture scrubs [NavDisplay]'s
 * matching pop transition.
 */
@Composable
fun AppNavContainer() {
    val backStack = rememberNavBackStack(Route.Main(initialPage = 0))
    val navigator = remember(backStack) { Navigator(backStack) }

    val entries =
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                    NavEntryDecorator { content ->
                        CompositionLocalProvider(LocalNavigator provides navigator) {
                            content.Content()
                        }
                    },
                ),
            entryProvider =
                entryProvider {
                    entry<Route.Main> { route ->
                        MainScreen(navigator, initialPage = route.initialPage)
                    }
                    entry<Route.MoeWallpaperCrop> { route ->
                        MoeWallpaperCropScreen(
                            navigator = navigator,
                            wallpaperUri = route.wallpaperUri,
                            initialZoom = route.initialZoom,
                            initialBiasX = route.initialBiasX,
                            initialBiasY = route.initialBiasY,
                        )
                    }
                    yumeSecondaryEntries(navigator)
                },
        )

    val sceneState =
        rememberSceneState(
            entries = entries,
            sceneStrategies = listOf(SinglePaneSceneStrategy()),
            sceneDecoratorStrategies = emptyList(),
            sharedTransitionScope = null,
            onBack = { navigator.pop() },
        )
    val scene = sceneState.currentScene

    val gestureState =
        rememberNavigationEventState(
            currentInfo = SceneInfo(scene),
            backInfo = sceneState.previousScenes.map { SceneInfo(it) },
        )

    NavigationBackHandler(
        state = gestureState,
        isBackEnabled = scene.previousEntries.isNotEmpty(),
        onBackCancelled = {},
        onBackCompleted = { navigator.pop() },
    )

    NavDisplay(
        sceneState = sceneState,
        navigationEventState = gestureState,
        contentAlignment = Alignment.TopStart,
        sizeTransform = null,
        transitionSpec = {
            ContentTransform(
                pushScaleEnter({ it }, TransformOrigin(1f, 0.5f)),
                pushScaleExit({ -it }, TransformOrigin(0f, 0.5f)),
                sizeTransform = null,
            )
        },
        popTransitionSpec = {
            ContentTransform(
                pushScaleEnter({ -it }, TransformOrigin(0f, 0.5f)),
                pushScaleExit({ it }, TransformOrigin(1f, 0.5f)),
                sizeTransform = null,
            )
        },
        predictivePopTransitionSpec = { _ ->
            ContentTransform(
                pushScaleEnter({ -it }, TransformOrigin(0f, 0.5f)),
                pushScaleExit({ it }, TransformOrigin(1f, 0.5f)),
                sizeTransform = null,
            )
        },
    )
}
