/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox.presentation.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.Navigator

// Right-pane only: keep transitions short and local (fade). No horizontal slides that feel global.
private const val SECONDARY_FADE = 160

/** Right-pane Navigation3 host used by the tablet dual-pane shell. */
@Composable
fun SecondaryDetailHost(
    backStack: MutableList<NavKey>,
    navigator: Navigator,
) {
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
                fadeIn(animationSpec = tween(SECONDARY_FADE, easing = LinearEasing)),
                fadeOut(animationSpec = tween(SECONDARY_FADE, easing = LinearEasing)),
                sizeTransform = null,
            )
        },
        popTransitionSpec = {
            ContentTransform(
                fadeIn(animationSpec = tween(SECONDARY_FADE, easing = LinearEasing)),
                fadeOut(animationSpec = tween(SECONDARY_FADE, easing = LinearEasing)),
                sizeTransform = null,
            )
        },
        predictivePopTransitionSpec = { _ ->
            ContentTransform(
                fadeIn(animationSpec = tween(SECONDARY_FADE, easing = LinearEasing)),
                fadeOut(animationSpec = tween(SECONDARY_FADE, easing = LinearEasing)),
                sizeTransform = null,
            )
        },
    )
}
