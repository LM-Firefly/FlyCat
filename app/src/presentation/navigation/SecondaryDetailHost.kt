/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

@file:Suppress("FunctionName")

package com.github.lmfirefly.flycat.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.github.lmfirefly.flycat.presentation.component.navigation.LocalNavigator
import com.github.lmfirefly.flycat.presentation.navigation.Navigator

@Composable
fun SecondaryDetailHost(navigator: Navigator, placeholderContent: (@Composable () -> Unit)? = null) {
    val componentContext = remember { DefaultComponentContext(LifecycleRegistry()) }
    val childStack = remember(componentContext, navigator) {
        componentContext.childStack(
            source = navigator.navigation,
            initialConfiguration = Route.About,
            serializer = null,
            handleBackButton = false,
        ) { rawRoute, _ ->
            DetailRouteChild(rawRoute as Route, navigator)
        }
    }
    val stack by childStack.subscribeAsState()
    val animation: StackAnimation<Any, DetailRouteChild> = remember { flyStackAnimation() }
    Children(
        stack = stack,
        modifier = Modifier.fillMaxSize(),
        animation = animation,
    ) { child ->
        val content = placeholderContent
        if (child.configuration is Route.About && content != null) {
            content()
        } else {
            child.instance.Content()
        }
    }
}

private class DetailRouteChild(private val route: Route, private val navigator: Navigator) {
    @Composable
    fun Content() {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            RouteContent(route, navigator)
        }
    }
}
