package com.github.yumelira.yumebox.screen.log

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.core.model.LogEntry
import com.github.yumelira.yumebox.core.model.LogFileInfo
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.CenteredText
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.Play
import com.github.yumelira.yumebox.presentation.icon.yume.PowerOff
import com.github.yumelira.yumebox.presentation.icon.yume.Share
import com.github.yumelira.yumebox.presentation.theme.AnimationSpecs
import com.github.yumelira.yumebox.util.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogScreen(navigator: Navigator) {
    val viewModel = koinViewModel<LogViewModel>()
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val logEntries by viewModel.tempLogEntries.collectAsStateWithLifecycle()

    var fabHidden by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val saveFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                if (!viewModel.saveTempLog(uri)) {
                    launch(Dispatchers.Main) { context.showToast("导出失败") }
                }
            }
        }

    val saveRecentLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val success = viewModel.exportRecentLogsToUri(uri)
            if (!success) {
                launch(Dispatchers.Main) {
                    context.showToast("保存失败")
                }
            }
        }
    }

    LaunchedEffect(listState) {
        var previousIndex = 0
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                fabHidden = index > previousIndex
                previousIndex = index
            }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            PollingTimers.ticks(PollingTimerSpecs.LogScreenRefresh).collect {
                viewModel.refreshTempLogEntries()
            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "日志录制",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = { saveRecentLogsLauncher.launch("lite-log-recent-${System.currentTimeMillis()}.log") },
                    ) {
                        Icon(imageVector = MiuixIcons.Download, contentDescription = "保存近期日志")
                    }
                    if (logEntries.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                saveFileLauncher.launch(
                                    "lite-log-${System.currentTimeMillis()}.txt"
                                )
                            }
                        ) {
                            Icon(imageVector = Yume.Share, contentDescription = "导出日志")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !fabHidden,
                enter =
                    scaleIn(
                        animationSpec =
                            tween(
                                durationMillis = AnimationSpecs.Proxy.FabDuration,
                                easing = AnimationSpecs.EmphasizedDecelerate,
                            ),
                        initialScale = AnimationSpecs.Proxy.VisibilityInitialScale,
                    ) +
                        fadeIn(
                            animationSpec =
                                tween(
                                    durationMillis = AnimationSpecs.Proxy.FabFadeDuration,
                                    easing = AnimationSpecs.EmphasizedDecelerate,
                                )
                        ),
                exit =
                    scaleOut(
                        animationSpec =
                            tween(
                                durationMillis = AnimationSpecs.Proxy.FabDuration,
                                easing = AnimationSpecs.EmphasizedDecelerate,
                            ),
                        targetScale = AnimationSpecs.Proxy.VisibilityTargetScale,
                    ) +
                        fadeOut(
                            animationSpec =
                                tween(
                                    durationMillis = AnimationSpecs.Proxy.FabFadeDuration,
                                    easing = AnimationSpecs.EmphasizedDecelerate,
                                )
                        ),
                label = "lite_log_fab",
            ) {
                FloatingActionButton(
                    modifier = Modifier.padding(end = 20.dp, bottom = 85.dp),
                    onClick = {
                        if (isRecording) viewModel.stopRecording() else viewModel.startRecording()
                    },
                ) {
                    Icon(
                        imageVector = if (isRecording) Yume.PowerOff else Yume.Play,
                        contentDescription = if (isRecording) "停止录制" else "开始录制",
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { innerPadding ->
        if (logEntries.isEmpty() && !isRecording) {
            CenteredText(firstLine = "还没有录制日志", secondLine = "点右下角开始录制。")
            return@Scaffold
        }

        if (logEntries.isEmpty() && isRecording) {
            CenteredText(firstLine = "正在等待日志输出", secondLine = "有新日志后会显示在这里。")
            return@Scaffold
        }

        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            lazyListState = listState,
        ) {
            itemsIndexed(
                items = logEntries.asReversed(),
                key = { index, item -> "${item.time}_${item.level}_${item.message}_$index" },
            ) { _, entry ->
                LogEntryRow(entry)
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val levelColor =
        when (entry.level) {
            LogMessage.Level.Debug -> Color(0xFF9E9E9E)
            LogMessage.Level.Info -> MiuixTheme.colorScheme.primary
            LogMessage.Level.Warning -> Color(0xFFFF9800)
            LogMessage.Level.Error -> Color(0xFFF44336)
            LogMessage.Level.Silent,
            LogMessage.Level.Unknown -> Color(0xFF9E9E9E)
        }

    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry.time,
                    style =
                        MiuixTheme.textStyles.body2.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = entry.level.name.uppercase().take(1),
                    style =
                        MiuixTheme.textStyles.body2.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = levelColor,
                )
            }
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = entry.message,
                style =
                    MiuixTheme.textStyles.body2.copy(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
