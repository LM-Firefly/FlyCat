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

package com.github.lmfirefly.flycat.feature.log.presentation.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.feature.log.presentation.viewmodel.LiveLogEntry
import com.github.lmfirefly.flycat.feature.log.presentation.viewmodel.LogConnectionState
import com.github.lmfirefly.flycat.feature.log.presentation.viewmodel.LogLevelFilter
import com.github.lmfirefly.flycat.feature.log.presentation.viewmodel.MihomoLogViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.SearchInputToolbar
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.misc.CenteredText
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.Search
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LevelFilters = listOf(
    LogLevelFilter.All,
    LogLevelFilter.Debug,
    LogLevelFilter.Info,
    LogLevelFilter.Warning,
    LogLevelFilter.Error,
)

private fun LogLevelFilter.displayName(): String = when (this) {
    LogLevelFilter.All -> FlyTxt.Log.Level.All
    LogLevelFilter.Debug -> FlyTxt.Log.Level.Debug
    LogLevelFilter.Info -> FlyTxt.Log.Level.Info
    LogLevelFilter.Warning -> FlyTxt.Log.Level.Warning
    LogLevelFilter.Error -> FlyTxt.Log.Level.Error
}

@Composable
fun MihomoLogScreen(navigator: Navigator) {
    val viewModel = koinViewModel<MihomoLogViewModel>()
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screen by viewModel.screenState.collectAsStateWithLifecycle()
    val filteredEntries = screen.filteredEntries
    val levelFilter = screen.levelFilter
    val connectionState = screen.connectionState
    val listState = rememberLazyListState()
    var followLatest by remember { mutableStateOf(true) }
    var showLevelPopup by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedLevelIndex = remember(levelFilter) { LevelFilters.indexOf(levelFilter).coerceAtLeast(0) }
    // 仅当此屏幕处于显示状态时，才保持原生订阅处于活跃状态。
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.setPreviewVisible(true)
    }
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        viewModel.setPreviewVisible(false)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.setPreviewVisible(false) }
    }
    // 自动跟随最新条目（最新优先列表的顶部），除非用户已手动滚动离开。
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(listState.isScrollInProgress, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }.collectLatest { (scrolling, index, offset) ->
            val atTop = index == 0 && offset == 0
            if (scrolling || atTop) followLatest = atTop
        }
    }
    LaunchedEffect(filteredEntries.firstOrNull()?.id) {
        if (filteredEntries.isNotEmpty() && followLatest) {
            listState.requestScrollToItem(0)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("text/plain")) { targetUri ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        val entries = viewModel.currentEntries()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(targetUri)?.use { output ->
                        entries.forEach { entry ->
                            output.write("[${entry.time}] [${entry.level.name}] ${entry.message}\n".toByteArray())
                        }
                    } != null
                } catch (_: Exception) {
                    false
                }
            }
            context.toast(
                if (ok) FlyTxt.Log.Mihomo.Export else FlyTxt.Log.Message.SaveFailed.format(FlyTxt.Util.Error.UnknownError),
            )
        }
    }
    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.Log.Mihomo.Title,
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator, extraStartPadding = 0.dp) },
                actions = {
                    Box {
                        IconButton(modifier = Modifier.padding(end = 4.dp), onClick = { showLevelPopup = true }) {
                            Icon(
                                imageVector = MiuixIcons.Filter,
                                contentDescription = FlyTxt.Log.Level.Filter,
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        OverlayListPopup(
                            show = showLevelPopup,
                            alignment = PopupPositionProvider.Align.BottomEnd,
                            onDismissRequest = { showLevelPopup = false },
                        ) {
                            ListPopupColumn {
                                LevelFilters.forEachIndexed { index, filter ->
                                    DropdownImpl(
                                        text = filter.displayName(),
                                        optionSize = LevelFilters.size,
                                        isSelected = selectedLevelIndex == index,
                                        onSelectedIndexChange = {
                                            if (filter != levelFilter) viewModel.setLevelFilter(filter)
                                            showLevelPopup = false
                                        },
                                        index = index,
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        modifier = Modifier.padding(end = 4.dp),
                        onClick = {
                            searchVisible = !searchVisible
                            // 与节点子页一致：收起即清空查询，避免搜索框隐藏后仍按旧查询过滤。
                            if (!searchVisible) {
                                searchQuery = ""
                                viewModel.setSearchQuery("")
                            }
                        },
                    ) {
                        Icon(
                            imageVector = FlyCat.Search,
                            contentDescription = FlyTxt.Component.Editor.Action.Search,
                        )
                    }
                    if (filteredEntries.isNotEmpty()) {
                        IconButton(onClick = { exportLauncher.launch("mihomo_log_${System.currentTimeMillis()}.txt") }) {
                            Icon(
                                imageVector = MiuixIcons.Download,
                                contentDescription = FlyTxt.Log.Mihomo.Export,
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            // 与「代理 → 策略组节点选择子页」共用同一搜索组件；Column 已扣除 topBar 高度（同节点子页布局），否则输入框会被顶栏遮挡而只露出空条（此前的显示异常）。
            AnimatedVisibility(visible = searchVisible, enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }), exit = fadeOut()) {
                SearchInputToolbar(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        viewModel.setSearchQuery(it)
                    },
                    modifier = Modifier.fillMaxWidth().padding(
                        start = UiDp.dp12,
                        end = UiDp.dp12,
                        top = UiDp.dp12,
                        bottom = UiDp.dp8,
                    ),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (filteredEntries.isEmpty()) {
                    val (firstLine, secondLine) = when {
                        connectionState == LogConnectionState.Connecting ->
                            FlyTxt.Log.Mihomo.Connecting to FlyTxt.Log.Mihomo.ConnectingHint
                        connectionState == LogConnectionState.Retrying ->
                            FlyTxt.Log.Mihomo.Retrying to FlyTxt.Log.Mihomo.RetryingHint
                        searchQuery.isNotBlank() ->
                            FlyTxt.Log.Mihomo.NoResults to FlyTxt.Log.Mihomo.SearchHint
                        levelFilter != LogLevelFilter.All ->
                            FlyTxt.Log.Mihomo.NoMatch to levelFilter.displayName()
                        else ->
                            FlyTxt.Log.Mihomo.NoLogs to FlyTxt.Log.Mihomo.LiveHint
                    }
                    CenteredText(firstLine = firstLine, secondLine = secondLine)
                } else {
                    ScreenLazyColumn(
                        scrollBehavior = scrollBehavior,
                        innerPadding = innerPadding,
                        lazyListState = listState,
                        // Column 已扣除 topBar 高度；contentPadding 优先于 innerPadding 生效（同节点子页）。
                        contentPadding = PaddingValues(top = UiDp.dp8, bottom = innerPadding.calculateBottomPadding()),
                    ) {
                        itemsIndexed(
                            items = filteredEntries,
                            key = { _, item -> item.id },
                            contentType = { _, _ -> "mihomo_log" },
                        ) { _, entry ->
                            MihomoLogEntryRow(
                                entry = entry,
                                onClick = { copyLogEntry(context, entry) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(160, easing = FastOutSlowInEasing),
                                    placementSpec = tween(260, easing = FastOutSlowInEasing),
                                    fadeOutSpec = tween(100),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MihomoLogEntryRow(entry: LiveLogEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val levelColor = when (entry.level) {
        com.github.lmfirefly.flycat.core.model.LogMessage.Level.Debug -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        com.github.lmfirefly.flycat.core.model.LogMessage.Level.Info -> MiuixTheme.colorScheme.primary
        com.github.lmfirefly.flycat.core.model.LogMessage.Level.Warning -> Color(0xFFFF9800)
        com.github.lmfirefly.flycat.core.model.LogMessage.Level.Error -> Color(0xFFF44336)
        com.github.lmfirefly.flycat.core.model.LogMessage.Level.Silent,
        com.github.lmfirefly.flycat.core.model.LogMessage.Level.Unknown,
            -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val interactionSource = remember { MutableInteractionSource() }
    Card(cornerRadius = AppTheme.radii.radius12, modifier = modifier.padding(vertical = 3.dp).clickable(interactionSource = interactionSource, indication = null, onClick = onClick)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = entry.time,
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = entry.level.name.uppercase().take(1),
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    color = levelColor,
                )
            }
            Spacer(modifier = Modifier.size(5.dp))
            Text(
                text = entry.message,
                style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun copyLogEntry(context: Context, entry: LiveLogEntry) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("FlyCat mihomo log", "[${entry.time}] [${entry.level.name}] ${entry.message}"))
    context.toast(FlyTxt.Log.Mihomo.Copied)
}
