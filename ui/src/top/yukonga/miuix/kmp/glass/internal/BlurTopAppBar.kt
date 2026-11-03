// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass.internal

// 本文件是 miuix-ui（玻璃分支）`basic/TopAppBar.kt` 中 BlurTopAppBar 及其内部布局的 vendored 副本，
// 供 miuix 0.9.4 制品（无 BlurTopAppBar）之上的 [GlassTopAppBar] 使用。除包名、可见性与导入外，
// 代码保持与上游逐字一致，便于后续重新同步；注释已译为中文，重同步时以代码语义比对为准。

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

/**
 * 大标题在折叠过程中逐渐模糊的 [BlurTopAppBar]。
 *
 * @param title [BlurTopAppBar] 的标题。
 * @param largeTitleBlurRadius 大标题折叠时的模糊程度，在标题完全淡出时达到该半径。
 * @param modifier 应用于 [BlurTopAppBar] 的修饰符。
 * @param color [BlurTopAppBar] 的背景色。
 * @param titleColor 折叠小标题文字的颜色。
 * @param largeTitle [BlurTopAppBar] 的大标题。
 * @param largeTitleColor 展开时大标题文字的颜色。
 * @param subtitle 显示在标题下方的副标题。
 * @param subtitleColor 副标题文字的颜色。
 * @param navigationIcon 导航图标的内容。
 * @param actions 操作图标的内容。
 * @param scrollBehavior 控制 [BlurTopAppBar] 的滚动行为。
 * @param defaultWindowInsetsPadding 是否应用默认的窗口衬垫（window insets）内边距。
 * @param titlePadding 标题与大标题的水平内边距。
 * @param navigationIconPadding 导航图标起始侧的内边距。
 * @param actionIconPadding 操作图标结束侧的内边距。
 * @param titleAlpha 绘制阶段施加于各标题容器（小标题、大标题、副标题）的不透明度。
 * @param bottomContent 显示在标题栏区域下方的内容。
 */
@Composable
internal fun BlurTopAppBar(
    title: String,
    largeTitleBlurRadius: Dp,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    largeTitle: String = title,
    largeTitleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    titleAlpha: () -> Float = { 1f },
    // FORK（FlyCat）：钉住模式——无大标题、不折叠，紧凑标题常显（SmallTopBar 语义）。
    pinned: Boolean = false,
    bottomContent: @Composable () -> Unit = {},
) {
    // 将传入的 actions 包进一个 Row。
    val actionsRow = @Composable {
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }

    // 以 TopAppBarLayout 作为内容进行组合。
    // 栏的背景色由 color 参数直接传入布局，无 Surface 包装。
    // 栏的高度由定义的常量高度（即 ContainerHeight 记号）减去栏的高度偏移得出。
    TopAppBarLayout(
        title = title,
        titleAlpha = titleAlpha,
        color = color,
        titleColor = titleColor,
        largeTitle = largeTitle,
        largeTitleColor = largeTitleColor,
        largeTitleBlurRadius = largeTitleBlurRadius,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        navigationIcon = navigationIcon,
        actions = actionsRow,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        pinned = pinned,
        bottomContent = bottomContent,
    )
}

/**
 * [TopAppBar] 的基础 [Layout]。该函数布局 [TopAppBar] 的导航图标（前置图标）、标题（头部）与操作图标（尾部图标）。
 * 注意导航图标与操作内容均为可选。
 *
 * @param title [TopAppBar] 的标题（头部）。
 * @param color [TopAppBar] 的背景色。
 * @param titleColor 折叠小标题文字的颜色。
 * @param largeTitleColor 展开时大标题文字的颜色。
 * @param subtitle 显示在标题下方的副标题文本。
 * @param subtitleColor 副标题文本的颜色。
 * @param navigationIcon 导航图标 [Composable]。
 * @param actions 操作图标 [Composable]。
 * @param titlePadding [TopAppBar] 标题与大标题的水平内边距。
 * @param navigationIconPadding 导航图标起始侧的内边距。
 * @param actionIconPadding 操作图标结束侧的内边距。
 * @param scrollBehavior 驱动折叠/展开状态的 [ScrollBehavior]。
 * 布局在布局/绘制阶段的 lambda 内直接读取其 `heightOffset` 与 `collapsedFraction`，并根据大标题的测量高度写入对应的 `heightOffsetLimit`。
 * @param modifier 应用于该布局的 [Modifier]。
 * @param largeTitle [TopAppBar] 的大标题，未指定时与 title 相同。
 * @param largeTitleBlurRadius 大标题折叠时的模糊程度。
 * @param defaultWindowInsetsPadding 是否为 [TopAppBar] 应用默认的窗口衬垫内边距。
 * @param titleAlpha 绘制阶段施加于各标题容器（小标题、大标题、副标题）的不透明度。
 * @param pinned 钉住模式（FORK）：无大标题、不折叠，紧凑标题常显。
 * @param bottomContent 显示在标题栏区域下方的组合内容。
 */
@Composable
private fun TopAppBarLayout(
    title: String,
    color: Color,
    titleColor: Color,
    largeTitleColor: Color,
    subtitle: String,
    subtitleColor: Color,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    titlePadding: Dp,
    navigationIconPadding: Dp,
    actionIconPadding: Dp,
    scrollBehavior: ScrollBehavior?,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    largeTitleBlurRadius: Dp = 0.dp,
    defaultWindowInsetsPadding: Boolean = true,
    titleAlpha: () -> Float = { 1f },
    // FORK（FlyCat）：钉住模式——无大标题、不折叠，紧凑标题常显。
    pinned: Boolean = false,
    bottomContent: @Composable () -> Unit = {},
) {
    val titleClip = remember { TopAppBarTitleClip() }
    // 生产者 lambda——读取保持在布局/绘制阶段，滚动永远不会重组此子树。
    val scrolledOffset = remember(scrollBehavior) {
        { scrollBehavior?.state?.heightOffset ?: 0f }
    }
    // 标题并非只是淡出——它在途中先失焦，如同源系统将其消散那样。
    // `largeTitleFade` 由淡出与模糊共享，使模糊恰好在标题最后一丝消失时达到峰值，而不是提前停住或拖过头。
    val largeTitleFade = remember(scrollBehavior, pinned) {
        {
            // FORK（FlyCat）：pinned 模式恒为完全折叠态，大标题始终完全淡出。
            if (pinned) {
                1f
            } else {
                val frac = scrollBehavior?.state?.collapsedFraction ?: 0f
                (frac * 3f).coerceIn(0f, 1f)
            }
        }
    }
    val largeTitleAlpha = remember(largeTitleFade) {
        {
            1f - largeTitleFade()
        }
    }
    val updateHeightOffsetLimit = remember(scrollBehavior) {
        { height: Int ->
            scrollBehavior?.state?.let { state ->
                val limit = -height.toFloat()
                if (state.heightOffsetLimit != limit) {
                    val restoringFromSmallTopAppBar = state.isPinnedBySmallBar
                    state.heightOffsetLimit = limit
                    // 钉住的小型栏自身高度保持不变，但仍保留 contentOffset。
                    if (restoringFromSmallTopAppBar) {
                        state.heightOffset = if (state.contentOffset < 0f) limit else 0f
                    }
                }
            }
            Unit
        }
    }

    // 布尔 derivedStateOf 只在翻转时失效重算——弹簧每次越过阈值只触发一次，而不是每帧触发。
    val smallTitleVisible by remember(scrollBehavior, pinned) {
        derivedStateOf {
            // FORK（FlyCat）：pinned 模式紧凑标题常显。
            if (pinned) {
                true
            } else {
                scrollBehavior?.state?.let { state ->
                    if (state.isPinnedBySmallBar) {
                        state.contentOffset < 0f
                    } else {
                        state.collapsedFraction * 3f >= 1f
                    }
                } ?: false
            }
        }
    }
    val smallTitleAlpha = remember { Animatable(if (smallTitleVisible) 1f else 0f) }
    val smallTitleTranslationY = remember { Animatable(if (smallTitleVisible) 0f else 20f) }

    LaunchedEffect(smallTitleVisible) {
        if (smallTitleVisible) {
            val showSpec = folmeSpring<Float>(damping = 1.0f, response = 0.3f)
            launch { smallTitleAlpha.animateTo(1f, showSpec) }
            launch { smallTitleTranslationY.animateTo(0f, showSpec) }
        } else {
            val hideSpec = folmeSpring<Float>(damping = 1.0f, response = 0.15f)
            launch { smallTitleAlpha.animateTo(0f, hideSpec) }
            launch { smallTitleTranslationY.animateTo(20f, hideSpec) }
        }
    }

    val animatedTitleColor by animateColorAsState(
        targetValue = titleColor,
        animationSpec = tween(durationMillis = 50),
    )
    val animatedLargeTitleColor by animateColorAsState(
        targetValue = largeTitleColor,
        animationSpec = tween(durationMillis = 50),
    )
    val animatedSubtitleColor by animateColorAsState(
        targetValue = subtitleColor,
        animationSpec = tween(durationMillis = 50),
    )

    Layout(
        {
            Box(
                Modifier
                    .layoutId("navigationIcon")
                    .padding(start = navigationIconPadding),
            ) {
                navigationIcon()
            }
            Box(
                Modifier
                    .layoutId("title")
                    .clipTopAppBarTitle(titleClip) { titleClip.titleOrigin }
                    .padding(horizontal = titlePadding)
                    .graphicsLayer {
                        alpha = smallTitleAlpha.value * titleAlpha()
                        translationY = smallTitleTranslationY.value
                    },
            ) {
                Text(
                    text = title,
                    color = animatedTitleColor,
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    fontWeight = FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            Box(
                Modifier
                    .layoutId("actionIcons")
                    .padding(end = actionIconPadding),
            ) {
                actions()
            }
            Box(
                Modifier
                    .layoutId("largeTitle")
                    .clipTopAppBarTitle(titleClip) { titleClip.largeTitleOrigin }
                    .padding(top = TopAppBarDefaults.CollapsedHeight)
                    .padding(horizontal = titlePadding)
                    .graphicsLayer {
                        alpha = largeTitleAlpha() * titleAlpha()
                        // 在每条路径上都会赋值，包括禁用路径：图层会保留上一次拿到的值，因此若半径回落到 0 而不赋值，最后一次的模糊便会永远留在标题上。
                        val radius = if (largeTitleBlurRadius > 0.dp) {
                            largeTitleBlurRadius.toPx() * largeTitleFade()
                        } else {
                            0f
                        }
                        renderEffect = if (radius > 0.1f) {
                            BlurEffect(radius, radius, TileMode.Decal)
                        } else {
                            null
                        }
                    },
            ) {
                Column(
                    modifier = Modifier
                        .offset {
                            val v = scrolledOffset()
                            IntOffset(0, v.fastRoundToInt())
                        }
                        .onSizeChanged { updateHeightOffsetLimit(it.height) },
                ) {
                    Text(
                        text = largeTitle,
                        color = animatedLargeTitleColor,
                        fontSize = MiuixTheme.textStyles.title1.fontSize,
                        fontWeight = FontWeight.Normal,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            color = animatedSubtitleColor,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }
            if (subtitle.isNotEmpty()) {
                Box(
                    Modifier
                        .layoutId("smallSubtitle")
                        .clipTopAppBarTitle(titleClip) { titleClip.subtitleOrigin }
                        .graphicsLayer {
                            alpha = smallTitleAlpha.value * titleAlpha()
                            translationY = smallTitleTranslationY.value
                        },
                ) {
                    Text(
                        text = subtitle,
                        color = animatedSubtitleColor,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
            Box(Modifier.layoutId("bottomContent")) {
                bottomContent()
            }
        },
        modifier = modifier
            .background(color)
            .then(
                if (defaultWindowInsetsPadding) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                } else {
                    Modifier
                },
            )
            // 标题保留视口裁剪；控件的阴影可以投出顶栏之外。
            .onSizeChanged { titleClip.viewportSize = it }
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .pointerInput(Unit) {
                detectTapGestures { /* 消费点击 */ }
            },
    ) { measurables, constraints ->
        val navigationIconPlaceable = measurables.fastFirst { it.layoutId == "navigationIcon" }.measure(constraints.copy(minWidth = 0, minHeight = 0))

        val actionIconsPlaceable = measurables.fastFirst { it.layoutId == "actionIcons" }.measure(constraints.copy(minWidth = 0, minHeight = 0))

        val maxTitleWidth = if (constraints.maxWidth == Constraints.Infinity) {
            constraints.maxWidth
        } else {
            (constraints.maxWidth - navigationIconPlaceable.width - actionIconsPlaceable.width)
                .coerceAtLeast(0)
        }
        val titleMaxWidth = if (maxTitleWidth == Constraints.Infinity) {
            maxTitleWidth
        } else {
            (maxTitleWidth * TITLE_WIDTH_FRACTION).fastRoundToInt()
        }

        val titlePlaceable = measurables.fastFirst { it.layoutId == "title" }.measure(constraints.copy(minWidth = 0, maxWidth = titleMaxWidth, minHeight = 0))

        val largeTitlePlaceable = measurables.fastFirst { it.layoutId == "largeTitle" }.measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            ),
        )

        val smallSubtitlePlaceable = measurables.firstOrNull { it.layoutId == "smallSubtitle" }
            ?.measure(constraints.copy(minWidth = 0, maxWidth = titleMaxWidth, minHeight = 0))

        val bottomContentPlaceable = measurables.fastFirst { it.layoutId == "bottomContent" }.measure(constraints.copy(minWidth = 0, minHeight = 0))

        val collapsedHeight = TopAppBarDefaults.CollapsedHeight.roundToPx()
        // largeTitle Box 顶部内边距 = collapsedHeight，计算纯展开量时把它减回去。
        // FORK（FlyCat）：pinned 模式不展开大标题。
        val expansion = if (pinned) 0 else (largeTitlePlaceable.height - collapsedHeight).coerceAtLeast(0)
        val barHeight = if (expansion > 0) {
            val offset = scrolledOffset()
            val collapseFraction = if (offset.isNaN()) {
                0f
            } else {
                (abs(offset) / expansion.toFloat()).coerceIn(0f, 1f)
            }
            lerp(
                start = collapsedHeight,
                stop = collapsedHeight + expansion,
                fraction = 1f - collapseFraction,
            )
        } else {
            collapsedHeight
        }

        val verticalCenter = collapsedHeight / 2
        val smallSubtitleHeight = smallSubtitlePlaceable?.height ?: 0
        val smallSubtitleBottom = verticalCenter + titlePlaceable.height / 2 + smallSubtitleHeight
        val expandedBottomPadding = if (smallSubtitlePlaceable != null) {
            TopAppBarDefaults.SubtitleBottomPadding.roundToPx()
        } else {
            TopAppBarDefaults.LargeTitleBottomPadding.roundToPx()
        }
        val contentTop = maxOf(barHeight + expandedBottomPadding, smallSubtitleBottom + expandedBottomPadding)
        val layoutHeight = contentTop + bottomContentPlaceable.height
        titleClip.contentHeight = layoutHeight

        layout(constraints.maxWidth, layoutHeight) {
            // 导航图标
            navigationIconPlaceable.placeRelative(
                x = 0,
                y = verticalCenter - navigationIconPlaceable.height / 2,
            )

            // 标题
            var baseX = (constraints.maxWidth - titlePlaceable.width) / 2
            if (baseX < navigationIconPlaceable.width) {
                baseX += (navigationIconPlaceable.width - baseX)
            } else if (baseX + titlePlaceable.width > constraints.maxWidth - actionIconsPlaceable.width) {
                baseX += ((constraints.maxWidth - actionIconsPlaceable.width) - (baseX + titlePlaceable.width))
            }
            val titleY = verticalCenter - titlePlaceable.height / 2
            titleClip.titleOrigin = IntOffset(
                if (layoutDirection == LayoutDirection.Ltr) baseX else constraints.maxWidth - baseX - titlePlaceable.width,
                titleY,
            )
            titlePlaceable.placeRelative(x = baseX, y = titleY)

            // 小副标题（在小标题下方居中，alpha 与小标题相同）
            smallSubtitlePlaceable?.let { subtitle ->
                val subtitleX = (constraints.maxWidth - subtitle.width) / 2
                val subtitleY = verticalCenter + titlePlaceable.height / 2
                titleClip.subtitleOrigin = IntOffset(
                    if (layoutDirection == LayoutDirection.Ltr) subtitleX else constraints.maxWidth - subtitleX - subtitle.width,
                    subtitleY,
                )
                subtitle.placeRelative(x = subtitleX, y = subtitleY)
            }

            // 操作图标
            actionIconsPlaceable.placeRelative(
                x = constraints.maxWidth - actionIconsPlaceable.width,
                y = verticalCenter - actionIconsPlaceable.height / 2,
            )

            // 大标题（在 Column 中含大副标题）
            if (!pinned) {
                titleClip.largeTitleOrigin = IntOffset(
                    if (layoutDirection == LayoutDirection.Ltr) 0 else constraints.maxWidth - largeTitlePlaceable.width,
                    0,
                )
                largeTitlePlaceable.placeRelative(x = 0, y = 0)
            }

            // 底部内容（固定放置在栏与副标题下方）
            bottomContentPlaceable.placeRelative(x = 0, y = contentTop)
        }
    }
}

/** 由布局发布、仅在绘制标题图层时读取的视口几何信息。 */
private class TopAppBarTitleClip {
    var viewportSize: IntSize by mutableStateOf(IntSize.Zero)
    var contentHeight: Int by mutableIntStateOf(0)
    var titleOrigin: IntOffset by mutableStateOf(IntOffset.Zero)
    var largeTitleOrigin: IntOffset by mutableStateOf(IntOffset.Zero)
    var subtitleOrigin: IntOffset by mutableStateOf(IntOffset.Zero)
}

/** 对变换后的标题保留原顶栏裁剪，同时不裁掉控件的阴影。 */
private fun Modifier.clipTopAppBarTitle(
    clip: TopAppBarTitleClip,
    origin: () -> IntOffset,
): Modifier = drawWithContent {
    val position = origin()
    val topInset = clip.viewportSize.height - clip.contentHeight
    clipRect(
        left = -position.x.toFloat(),
        top = -position.y.toFloat() - topInset,
        right = clip.viewportSize.width - position.x.toFloat(),
        bottom = clip.contentHeight - position.y.toFloat(),
    ) {
        this@drawWithContent.drawContent()
    }
}

// 余量：避免居中的标题紧贴导航/操作区。
private const val TITLE_WIDTH_FRACTION = 0.9

/**
 * 0.9.4 的 [TopAppBarState.pinnedBySmallTopAppBar] 是模块 internal，vendored 副本无法访问。
 * 本副本不含 SmallTopAppBar，该标记恒为 `false`，其恢复路径为死代码。
 */
private val TopAppBarState.isPinnedBySmallBar: Boolean get() = false
