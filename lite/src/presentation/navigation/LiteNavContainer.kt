package com.github.yumelira.yumebox.presentation.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.screen.about.AboutScreen
import com.github.yumelira.yumebox.screen.home.HomeScreen
import com.github.yumelira.yumebox.screen.importconfig.ImportConfigScreen
import com.github.yumelira.yumebox.screen.log.LogScreen
import com.github.yumelira.yumebox.screen.proxy.NodesScreen
import com.github.yumelira.yumebox.screen.proxy.ProvidersScreen
import com.github.yumelira.yumebox.screen.settings.AccessControlScreen
import com.github.yumelira.yumebox.screen.settings.VpnSettingsScreen

private const val DURATION = 340
private const val FADE_DURATION = 140
private val slideEasing = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.0f)

private fun slideEnter(offset: (Int) -> Int): EnterTransition =
    slideInHorizontally(animationSpec = tween(DURATION, easing = slideEasing), initialOffsetX = offset) +
        fadeIn(animationSpec = tween(FADE_DURATION, easing = LinearEasing))

private fun slideExit(offset: (Int) -> Int): ExitTransition =
    slideOutHorizontally(animationSpec = tween(DURATION, easing = slideEasing), targetOffsetX = offset) +
        fadeOut(animationSpec = tween(FADE_DURATION, easing = LinearEasing))

@Composable
fun LiteNavContainer(pendingImportUrl: String? = null) {
    val startKey = if (pendingImportUrl != null) Route.ImportConfig(prefillUrl = pendingImportUrl) else Route.Home
    val backStack = rememberNavBackStack(startKey)
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
                    entry<Route.Home> { HomeScreen(navigator) }
                    entry<Route.Nodes> { NodesScreen(navigator) }
                    entry<Route.Providers> { ProvidersScreen(navigator) }
                    entry<Route.ImportConfig> { route ->
                        ImportConfigScreen(navigator, prefillUrl = route.prefillUrl)
                    }
                    entry<Route.VpnSettings> { VpnSettingsScreen(navigator) }
                    entry<Route.AccessControl> { AccessControlScreen(navigator) }
                    entry<Route.Log> { LogScreen(navigator) }
                    entry<Route.About> { AboutScreen(navigator) }
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
            ContentTransform(slideEnter { it }, slideExit { -it }, sizeTransform = null)
        },
        popTransitionSpec = {
            ContentTransform(slideEnter { -it }, slideExit { it }, sizeTransform = null)
        },
        predictivePopTransitionSpec = { _ ->
            ContentTransform(slideEnter { -it }, slideExit { it }, sizeTransform = null)
        },
    )
}
