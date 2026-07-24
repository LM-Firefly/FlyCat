/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

@file:Suppress("FunctionName")

package com.github.yumelira.yumebox.presentation.navigation


import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.Navigator

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
            splitShellRightPaneTransform(forward = true)
        },
        popTransitionSpec = {
            splitShellRightPaneTransform(forward = false)
        },
        predictivePopTransitionSpec = { _ ->
            splitShellRightPaneTransform(forward = false)
        },
    )
}
