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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.ArrowDownUp
import com.github.lmfirefly.flycat.presentation.icon.flycat.Bolt
import com.github.lmfirefly.flycat.presentation.icon.flycat.House
import com.github.lmfirefly.flycat.presentation.icon.flycat.PackageCheck
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.isDarkTheme
import com.github.lmfirefly.flycat.presentation.util.LocalBottomBarScrollBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.CompositeBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassMaterials
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
    // 内容录制层在无内容区域是透明的，单独采样会让底栏玻璃在这些区域"全透"（issue #220）；
    // 垫上不透明的壁纸录制层合成采样，整条底栏的模糊与颜色层处处一致生效。
    val wallpaperBackdrop = LocalGlassBackdrop.current
    val glassSample = remember(wallpaperBackdrop, backdrop) {
        if (wallpaperBackdrop == null) backdrop else CompositeBackdrop(wallpaperBackdrop, backdrop)
    }
    FloatingBottomBarContent(isVisible = isVisible, backdrop = glassSample)
}

/**
 * 底栏玻璃材质：在 [GlassMaterials.puredThinGlass] 基础上，把最靠近背景的颜色层换成平涂染色
 * （亮色 8% 白 / 暗色 8% 黑，issue #220），轻微压低身后繁忙内容（流向图、节点卡片）透出后的对比度，减轻与图标、标签的叠影。
 * 后两层保持不变（亮色为亮泽层，暗色为去饱和与压暗层）；原第一层 PlusDarker 仅为轻度压暗（亮色 2% / 暗色 10%），替换后整体观感几乎无损。
 */
private fun bottomBarGlassMaterial(isDark: Boolean): GlassMaterial {
    val tint = if (isDark) Color(0x14000000) else Color(0x14FFFFFF)
    return GlassMaterials.puredThinGlass(isDark).copy(
//        blurRadius = 12.dp,
        first = GlassColorLayer(tint, GlassColorBlendMode.SrcOver),
    )
}

@Composable
private fun FloatingBottomBarContent(isVisible: Boolean = true, backdrop: Backdrop) {
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
        material = bottomBarGlassMaterial(isDarkTheme()),
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
