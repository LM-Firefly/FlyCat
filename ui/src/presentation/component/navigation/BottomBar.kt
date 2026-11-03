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

package com.github.lmfirefly.flycat.presentation.component.navigation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.ArrowDownUp
import com.github.lmfirefly.flycat.presentation.icon.flycat.Bolt
import com.github.lmfirefly.flycat.presentation.icon.flycat.House
import com.github.lmfirefly.flycat.presentation.icon.flycat.PackageCheck
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.LocalBottomBarScrollBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max

class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true
        val layoutInfo = pagerState.layoutInfo
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val currentDistanceInPages = targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
        val scrollPixels = currentDistanceInPages * pageSize

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.animateScrollBy(
                    value = scrollPixels,
                    animationSpec = MainBottomBarDefaults.PagerAnimationSpec,
                )
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainPagerState = remember(pagerState, coroutineScope) { MainPagerState(pagerState, coroutineScope) }

val LocalPagerState = compositionLocalOf<PagerState> { error("LocalPagerState is not provided") }
val LocalMainPagerState = compositionLocalOf<MainPagerState> { error("LocalMainPagerState is not provided") }
val LocalHandlePageChange = compositionLocalOf<(Int) -> Unit> { error("LocalHandlePageChange is not provided") }
val LocalNavigator = compositionLocalOf<Navigator> { error("LocalNavigator is not provided") }
/** 非空时表示平板双栏外壳处于活动状态；用于驱动详情面板导航。*/
val LocalDetailNavigator = compositionLocalOf<Navigator?> { null }

/** 当内容在平板双窗格外壳内托管时为 True。 */
inline val Navigator?.isSplitShell: Boolean
    get() = this != null

object MainBottomBarDefaults {
    val HorizontalPadding = UiDp.dp64
    val TopPadding = UiDp.dp6
    val FloatingBottomPadding = UiDp.dp10
    val FloatingReservedHeight = UiDp.dp68
    val PagerAnimationSpec: AnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = Int.VisibilityThreshold.toFloat(),
    )
}

@Composable
fun rememberMainPagerFlingBehavior(pagerState: PagerState): TargetedFlingBehavior = PagerDefaults.flingBehavior(
    state = pagerState,
    snapAnimationSpec = MainBottomBarDefaults.PagerAnimationSpec,
)

@Composable
fun rememberBottomBarReservedHeight(): Dp {
    val density = LocalDensity.current
    val systemBottomInset = with(density) {
        max(
            WindowInsets.navigationBars.getBottom(this),
            WindowInsets.systemGestures.getBottom(this),
        ).toDp()
    }
    return remember(systemBottomInset) {
        MainBottomBarDefaults.FloatingReservedHeight + systemBottomInset
    }
}

@Composable
fun BottomBarContent(isVisible: Boolean = true, backdrop: LayerBackdrop) {
    FloatingBottomBarContent(isVisible = isVisible, backdrop = backdrop)
}

@Composable
private fun FloatingBottomBarContent(isVisible: Boolean = true, backdrop: LayerBackdrop) {
    val bottomBarScrollBehavior = LocalBottomBarScrollBehavior.current
    val mainPagerState = LocalMainPagerState.current
    val page by remember(mainPagerState) { derivedStateOf { mainPagerState.selectedPage } }
    val bottomBarVisible = isVisible && (bottomBarScrollBehavior?.isBottomBarVisible ?: true)
    val density = LocalDensity.current
    val handlePageChange = LocalHandlePageChange.current
    val onItemClick: (Int) -> Unit = { index ->
        if (index != mainPagerState.selectedPage) {
            handlePageChange(index)
        }
    }
    val bottomSafeInset = with(density) {
        val navigationBottom = WindowInsets.navigationBars.getBottom(this)
        val gestureBottom = WindowInsets.systemGestures.getBottom(this)
        max(navigationBottom, gestureBottom).toDp()
    }
    // miuix-glass 浮动导航栏：玻璃胶囊 + 指针跟随指示器；显隐交给组件的 visible 过渡（收缩/淡出/模糊消失）。
    GlassNavigationBar(
        items = BottomBarDestination.entries.map { destination ->
            GlassNavigationItem(
                icon = destination.icon,
                label = destination.label,
                contentDescription = destination.label,
            )
        },
        selectedIndex = page,
        onSelect = onItemClick,
        backdrop = backdrop,
        visible = bottomBarVisible,
        height = UiDp.dp48,
        indicatorColor = MiuixTheme.colorScheme.primary.copy(alpha = AppTheme.opacity.subtle),
        selectedColor = MiuixTheme.colorScheme.primary,
        unselectedColor = MiuixTheme.colorScheme.onSurface.copy(alpha = AppTheme.opacity.secondaryText),
        modifier = Modifier.fillMaxWidth().padding(
            start = MainBottomBarDefaults.HorizontalPadding,
            end = MainBottomBarDefaults.HorizontalPadding,
            top = MainBottomBarDefaults.TopPadding,
            bottom = bottomSafeInset + MainBottomBarDefaults.FloatingBottomPadding,
        ),
    )
}

enum class BottomBarDestination(val icon: ImageVector) {
    Home(FlyCat.House),
    Proxy(FlyCat.ArrowDownUp),
    Config(FlyCat.PackageCheck),
    Setting(FlyCat.Bolt);

    val label: String get() = when (this) {
        Home -> FlyTxt.Component.BottomBar.Home
        Proxy -> FlyTxt.Component.BottomBar.Proxy
        Config -> FlyTxt.Component.BottomBar.Config
        Setting -> FlyTxt.Component.BottomBar.Setting
    }
}
