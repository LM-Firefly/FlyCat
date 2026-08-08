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

package com.github.yumeyucca.yumebox.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.*
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.androidPredictiveBackAnimatableV1
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.essenty.backhandler.BackHandler
import com.github.yumeyucca.yumebox.MainScreen
import com.github.yumeyucca.yumebox.presentation.component.LocalNavigator
import com.github.yumeyucca.yumebox.presentation.component.Navigator
import com.github.yumeyucca.yumebox.screen.settings.MoeWallpaperCropScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalDecomposeApi::class)
private fun <T : Any> yumeAnimation(
    backHandler: BackHandler,
    onBack: () -> Unit,
): StackAnimation<Any, T> =
    predictiveBackAnimation(
        backHandler = backHandler,
        onBack = onBack,
        fallbackAnimation =
            stackAnimation(fade(tween(300)) + slide(tween(400)) + scale(tween(500))),
        selector = { event, _, _ -> androidPredictiveBackAnimatableV1(event) },
    )

class AppNavigationComponent(
    val componentContext: ComponentContext,
    val predictiveBackEnabledAtLaunch: Boolean,
) {
    val navigator = Navigator(listOf(Route.Main()))
    internal val childStack =
        componentContext.childStack(
            source = navigator.navigation,
            initialConfiguration = Route.Main(),
            serializer = null,
            // predictiveBackAnimation is the sole system-back callback for this stack. Keeping
            // childStack's own callback enabled lets fast gestures bypass the fallback animation.
            handleBackButton = false,
        ) { rawRoute, _ ->
            RouteChild(rawRoute as Route, navigator)
        }
}

internal class RouteChild(
    private val route: Route,
    private val navigator: Navigator,
) {
    @Composable
    fun Content() {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            when (route) {
                is Route.Main -> MainScreen(navigator, route.initialPage)
                is Route.MoeWallpaperCrop ->
                    MoeWallpaperCropScreen(
                        navigator,
                        route.wallpaperUri,
                        route.initialZoom,
                        route.initialBiasX,
                        route.initialBiasY,
                    )

                else -> RouteContent(route, navigator)
            }
        }
    }
}

@Composable
fun AppNavContainer(component: AppNavigationComponent) {
    val componentContext = component.componentContext
    val navigator = component.navigator
    val predictiveBackEnabled = component.predictiveBackEnabledAtLaunch
    val scope = rememberCoroutineScope()
    val commitBack: () -> Unit = remember(navigator, scope) {
        {
            scope.launch {
                // A fast system gesture can complete without a progress frame. Commit after the
                // platform transaction has closed so Children can start its fallback animation.
                withFrameNanos { }
                navigator.pop()
            }
            Unit
        }
    }
    val stack by component.childStack.subscribeAsState()
    SideEffect {
        navigator.syncBackStack(stack.items.map { it.configuration })
    }
    if (!predictiveBackEnabled) {
        BackHandler(enabled = stack.backStack.isNotEmpty(), onBack = commitBack)
    }
    val animation: StackAnimation<Any, RouteChild> = remember(component) {
        if (predictiveBackEnabled) {
            yumeAnimation(componentContext.backHandler, commitBack)
        } else {
            stackAnimation(fade(tween(300)) + slide(tween(400)) + scale(tween(500)))
        }
    }
    Children(
        stack = stack,
        modifier = Modifier.fillMaxSize(),
        animation = animation,
    ) { child ->
        child.instance.Content()
    }
}
