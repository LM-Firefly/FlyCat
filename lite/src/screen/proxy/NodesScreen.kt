package com.github.yumelira.yumebox.screen.proxy

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.feature.proxy.presentation.screen.ProxyPager
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun NodesScreen(navigator: Navigator) {
    val homeViewModel = koinViewModel<HomeViewModel>()
    val isRunning by homeViewModel.isRunning.collectAsStateWithLifecycle()

    ProxyPager(
        mainInnerPadding = PaddingValues(),
        onNavigateToProviders = { navigator.push(Route.Providers) },
        isActive = isRunning,
    )
}
