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

package com.github.lmfirefly.flycat.presentation.component.state

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.zIndex
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.glassCardSurface
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.Sizes
import com.github.lmfirefly.flycat.presentation.theme.Spacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 从屏幕层传入的搜索栏内缩三元组；未指定的字段回退到主题默认值。 */
data class SearchBarPadding(
    val top: Dp = Dp.Unspecified,
    val start: Dp = Dp.Unspecified,
    val end: Dp = Dp.Unspecified,
)

@Composable
fun SearchStatus.TopAppBarAnim(
    modifier: Modifier = Modifier,
    visible: Boolean = shouldCollapse(),
    content: @Composable () -> Unit,
) {
    val alpha by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(if (visible) AnimationSpecs.DURATION_SLOW else 0, easing = AnimationSpecs.StandardEasing),
            label = "SearchTopBarAlpha",
        )
    Box(
        // 顶栏背景由其自身的渐进色带负责（静止时透明、内容滚入渐显）；此处仅做整体淡出，勿垫不透明底色，否则静止时会盖住壁纸渐变形成灰白色块。
        modifier = modifier.fillMaxWidth().graphicsLayer {
            this.alpha = alpha
        }
    ) {
        content()
    }
}

@Composable
fun SearchStatus.SearchPager(
    onSearchStatusChange: (SearchStatus) -> Unit,
    defaultResult: @Composable () -> Unit = {},
    emptyResult: @Composable () -> Unit = {},
    padding: SearchBarPadding = SearchBarPadding(),
    result: @Composable () -> Unit,
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes
    val resolvedPadding = SearchBarPadding(
        top = padding.top.takeOrElse { componentSizes.searchBarTopPadding },
        start = padding.start.takeOrElse { spacing.space0 },
        end = padding.end.takeOrElse { spacing.space0 },
    )

    val searchStatus = this
    val isCollapsed = searchStatus.isCollapsed()
    val isExpanded = searchStatus.isExpanded()
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val topPadding by animateDpAsState(
        targetValue = if (searchStatus.shouldExpand()) {
            systemBarsPadding + componentSizes.listItemVerticalMinimal
        } else {
            max(searchStatus.offsetY, spacing.space0)
        },
        animationSpec = tween(AnimationSpecs.DURATION_NORMAL, easing = AnimationSpecs.EnterEasing),
        label = "SearchTopPadding",
        finishedListener = { onSearchStatusChange(searchStatus.onAnimationComplete()) },
    )
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (searchStatus.shouldExpand()) 1f else 0f,
        // 使 topPadding 的持续时间/缓动与表面变暗和布局的稳定保持一致。
        // 此前，变暗效果在100毫秒前就已完成（200 vs 300），导致内容在收起过程中暴露，并被误读为动画结束时的卡顿。
        animationSpec = tween(AnimationSpecs.DURATION_NORMAL, easing = AnimationSpecs.EnterEasing),
        label = "SearchSurfaceAlpha",
    )

    BackHandler(enabled = !isCollapsed) {
        onSearchStatusChange(
            searchStatus.copy(searchText = "", current = SearchStatus.Status.COLLAPSING)
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().zIndex(5f).background(colorScheme.surface.copy(alpha = surfaceAlpha)).then(
            if (!isCollapsed) {
                Modifier.pointerInput(searchStatus.current) {}
            } else {
                Modifier
            }
        )
    ) {
        SearchPagerTopRow(
            searchStatus = searchStatus,
            onSearchStatusChange = onSearchStatusChange,
            topPadding = topPadding,
            barPadding = resolvedPadding,
        )
        SearchPagerResultsLayer(
            searchStatus = searchStatus,
            isExpanded = isExpanded,
            defaultResult = defaultResult,
            emptyResult = emptyResult,
            result = result,
        )
    }
}

@Composable
private fun SearchPagerTopRow(
    searchStatus: SearchStatus,
    onSearchStatusChange: (SearchStatus) -> Unit,
    topPadding: Dp,
    barPadding: SearchBarPadding,
) {
    val isCollapsed = searchStatus.isCollapsed()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding).then(
            if (!isCollapsed) {
                Modifier.background(colorScheme.surface)
            } else {
                Modifier
            }
        ),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isCollapsed) {
            SearchBar(
                searchStatus = searchStatus,
                onSearchStatusChange = onSearchStatusChange,
                padding = barPadding,
                modifier = Modifier.weight(1f),
            )
        }

        SearchPagerCancelButton(
            searchStatus = searchStatus,
            onSearchStatusChange = onSearchStatusChange,
            searchBarTopPadding = barPadding.top,
        )
    }
}

@Composable
private fun SearchPagerCancelButton(
    searchStatus: SearchStatus,
    onSearchStatusChange: (SearchStatus) -> Unit,
    searchBarTopPadding: Dp,
) {
    val spacing = AppTheme.spacing
    val isExpanded = searchStatus.isExpanded() || searchStatus.isExpanding()
    AnimatedVisibility(
        visible = isExpanded,
        // 仅对布局宽度（展开/收缩）+淡入淡出进行动画处理。
        // 之前的滑入/滑出动画与同时进行的宽度动画相互冲突（滑动偏移量依赖于收缩的宽度），
        // 导致移除时相邻的 `weight(1f)` 搜索栏出现卡顿的重新布局。去掉滑动动画后，空间回收过程保持平滑流畅。
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        Text(
            text = FlyTxt.Component.Button.Cancel,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
            modifier = Modifier.padding(
                start = spacing.space4,
                end = spacing.space16,
                top = searchBarTopPadding,
                bottom = spacing.space6,
            ).clickable(
                interactionSource = null,
                indication = null,
                enabled = searchStatus.isExpanded(),
            ) {
                onSearchStatusChange(
                    searchStatus.copy(
                        searchText = "",
                        current = SearchStatus.Status.COLLAPSING,
                    )
                )
            },
        )
    }
}

@Composable
private fun SearchPagerResultsLayer(
    searchStatus: SearchStatus,
    isExpanded: Boolean,
    defaultResult: @Composable () -> Unit,
    emptyResult: @Composable () -> Unit,
    result: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = isExpanded,
        modifier = Modifier.fillMaxSize().zIndex(1f),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        when (searchStatus.resultStatus) {
            SearchStatus.ResultStatus.DEFAULT -> defaultResult()
            SearchStatus.ResultStatus.EMPTY -> emptyResult()
            SearchStatus.ResultStatus.SHOW -> result()
        }
    }
}

@Composable
private fun SearchBar(
    searchStatus: SearchStatus,
    onSearchStatusChange: (SearchStatus) -> Unit,
    padding: SearchBarPadding,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes

    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(searchStatus.searchText)) }

    LaunchedEffect(searchStatus.searchText) {
        if (textFieldValue.text != searchStatus.searchText) {
            textFieldValue = TextFieldValue(searchStatus.searchText)
        }
    }

    LaunchedEffect(searchStatus.current) {
        if (searchStatus.isExpanding()) {
            focusRequester.requestFocus()
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            onSearchStatusChange(searchStatus.copy(searchText = it.text))
        },
        singleLine = true,
        textStyle = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            color = colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = padding.start, end = padding.end)
            .padding(top = padding.top, bottom = componentSizes.searchBarBottomPadding)
            .heightIn(min = componentSizes.searchFieldMinHeight)
            .glassCardSurface(CircleShape, fallbackColor = colorScheme.secondaryContainer)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBarLeadingIcon(componentSizes = componentSizes, spacing = spacing)
                Box(modifier = Modifier.weight(1f)) { innerTextField() }
                SearchBarClearButton(
                    searchText = searchStatus.searchText,
                    onClear = {
                        textFieldValue = TextFieldValue("")
                        onSearchStatusChange(searchStatus.copy(searchText = ""))
                    },
                    componentSizes = componentSizes,
                    spacing = spacing,
                )
            }
        },
    )
}

@Composable
private fun SearchBarLeadingIcon(componentSizes: Sizes, spacing: Spacing) {
    Icon(
        imageVector = MiuixIcons.Basic.Search,
        contentDescription = FlyTxt.Component.Editor.Action.Search,
        modifier = Modifier.size(componentSizes.searchIconTouchTarget).padding(start = spacing.space16, end = spacing.space8),
        tint = colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun SearchBarClearButton(
    searchText: String,
    onClear: () -> Unit,
    componentSizes: Sizes,
    spacing: Spacing,
) {
    AnimatedVisibility(
        visible = searchText.isNotEmpty(),
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        Icon(
            imageVector = MiuixIcons.Basic.SearchCleanup,
            contentDescription = FlyTxt.Component.Button.Clear,
            tint = colorScheme.onSurface,
            modifier = Modifier.size(componentSizes.searchIconTouchTarget).padding(start = spacing.space8, end = spacing.space16)
            .clickable(interactionSource = null, indication = null) { onClear() },
        )
    }
}
