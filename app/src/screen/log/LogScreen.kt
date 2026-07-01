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

package com.github.yumelira.yumebox.screen.log

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.core.model.LogEntry
import com.github.yumelira.yumebox.core.model.LogFileInfo
import com.github.yumelira.yumebox.core.model.LogMessage
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.CenteredText
import com.github.yumelira.yumebox.presentation.component.NavigationBackIcon
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.Play
import com.github.yumelira.yumebox.presentation.icon.yume.PowerOff
import com.github.yumelira.yumebox.presentation.icon.yume.Share
import com.github.yumelira.yumebox.presentation.icon.yume.Square
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.theme.AnimationSpecs
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import dev.oom_wg.purejoy.mlang.MLang
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.SinkFeedback

@Composable
fun LogScreen(navigator: Navigator) {
    val viewModel = koinViewModel<LogViewModel>()
    val scrollBehavior = MiuixScrollBehavior()

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val logFiles by viewModel.logFiles.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val saveRecentLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { targetUri ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = viewModel.exportRecentLogsToUri(targetUri)
            if (success) {
                context.toast(MLang.Log.Message.RecentLogSaved)
            } else {
                context.toast(MLang.Log.Message.SaveFailed.format(MLang.Util.Error.UnknownError))
            }
        }
    }

    LaunchedEffect(Unit) {
        PollingTimers.ticks(PollingTimerSpecs.LogScreenRefresh).collect {
            viewModel.refreshLogFiles()
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = MLang.Log.Title,
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator, extraStartPadding = 0.dp) },
            )
        },
        floatingActionButton = {
            Column(
                modifier = Modifier.padding(end = 20.dp, bottom = 85.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                FloatingActionButton(
                    onClick = {
                        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        saveRecentLogsLauncher.launch("flycat_log_$ts.log")
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Download,
                        contentDescription = MLang.Log.Action.SaveRecentLogs,
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
                FloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            viewModel.stopRecording()
                        } else {
                            viewModel.startRecording()
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isRecording) Yume.Square else Yume.Play,
                        contentDescription =
                            if (isRecording) MLang.Log.Action.StopRecording else MLang.Log.Action.StartRecording,
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
                FloatingActionButton(
                    onClick = { viewModel.deleteAllLogs() },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = MLang.Log.Action.ClearLogs,
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { innerPadding ->
        if (logFiles.isEmpty()) {
            CenteredText(
                firstLine = MLang.Log.Empty.NoLogs,
                secondLine = MLang.Log.Empty.StartRecordingHint,
            )
        } else {
            ScreenLazyColumn(
                scrollBehavior = scrollBehavior,
                innerPadding = innerPadding,
                topPadding = 20.dp,
            ) {
                items(logFiles.size, key = { idx -> logFiles[idx].name }) { index ->
                    LogFileItem(
                        fileInfo = logFiles[index],
                        index = index,
                        onClick = {
                            navigator.push(Route.LogDetail(fileName = logFiles[index].name))
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun LogDetailScreen(
    navigator: Navigator,
    fileName: String,
) {
    val viewModel = koinViewModel<LogViewModel>()
    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { targetUri ->
        if (targetUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val success = viewModel.exportLogToUri(fileName, targetUri)
            if (success) {
                context.toast(MLang.Log.Message.Saved.format(fileName))
            } else {
                context.toast(MLang.Log.Message.SaveFailed.format(MLang.Util.Error.UnknownError))
            }
        }
    }
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isCurrentFileRecording = viewModel.isCurrentFileRecording(fileName) && isRecording
    var logEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(fileName) {
        logEntries = viewModel.readLogContent(fileName).asReversed()
        isLoading = false
    }
    LaunchedEffect(isCurrentFileRecording, fileName) {
        if (!isCurrentFileRecording) return@LaunchedEffect
        PollingTimers.ticks(PollingTimerSpecs.LogScreenRefresh).collect {
            logEntries = viewModel.readLogContent(fileName).asReversed()
        }
    }
    Scaffold(
        topBar = {
            TopBar(
                title = if (isCurrentFileRecording) MLang.Log.Detail.RealTimeLog else fileName,
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator, extraStartPadding = 0.dp) },
            )
        },
        floatingActionButton = {
            Column(
                modifier = Modifier.padding(end = 20.dp, bottom = 85.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (isCurrentFileRecording) {
                    FloatingActionButton(
                        onClick = { viewModel.stopRecording() },
                    ) {
                        Icon(
                            imageVector = Yume.Square,
                            contentDescription = MLang.Log.Action.Pause,
                            tint = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
                FloatingActionButton(
                    onClick = {
                        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val baseName = fileName.removeSuffix(".log")
                        exportLauncher.launch("${baseName}_$ts.log")
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Download,
                        contentDescription = MLang.Log.Action.Save,
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
                FloatingActionButton(
                    onClick = {
                        viewModel.deleteLogFile(fileName)
                        navigator.navigateUp()
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = MLang.Log.Action.Delete,
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { innerPadding ->
        when {
            isLoading -> {
                CenteredText(
                    firstLine = MLang.Log.Detail.Loading,
                    secondLine = "",
                )
            }
            logEntries.isEmpty() -> {
                CenteredText(
                    firstLine = if (isCurrentFileRecording) MLang.Log.Detail.WaitingLog else MLang.Log.Detail.LogEmpty,
                    secondLine = if (isCurrentFileRecording) {
                        MLang.Log.Detail.WillShowWhenGenerated
                    } else {
                        MLang.Log.Detail.NoLogContent
                    },
                )
            }
            else -> {
                ScreenLazyColumn(
                    scrollBehavior = scrollBehavior,
                    innerPadding = innerPadding,
                    topPadding = 20.dp,
                ) {
                    items(logEntries.size, key = { idx -> "${logEntries[idx].time}_${logEntries[idx].level}_${idx}" }) { index ->
                        LogEntryCard(
                            entry = logEntries[index],
                            index = index,
                            isNewEntry = isCurrentFileRecording && index < 3,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry, index: Int = 0, isNewEntry: Boolean = false) {
    val levelColor =
        when (entry.level) {
            LogMessage.Level.Debug -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            LogMessage.Level.Info -> MiuixTheme.colorScheme.primary
            LogMessage.Level.Warning -> androidx.compose.ui.graphics.Color(0xFFFF9800)
            LogMessage.Level.Error -> androidx.compose.ui.graphics.Color(0xFFF44336)
            LogMessage.Level.Silent,
            LogMessage.Level.Unknown,
                -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        }
    var visible by remember { mutableStateOf(!isNewEntry) }
    LaunchedEffect(Unit) {
        if (isNewEntry) {
            delay(index * 50L)
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
            animationSpec = tween(200),
            initialOffsetY = { -it / 2 },
        ),
    ) {
        Card(modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
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
                        style = MiuixTheme.textStyles.body2.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = levelColor,
                    )
                }
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = entry.message,
                    style = MiuixTheme.textStyles.body2.copy(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LogFileItem(
    fileInfo: LogFileInfo,
    index: Int,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val interactionSource = remember { MutableInteractionSource() }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        visible = true
    }

    val animatedSize by animateFloatAsState(
        targetValue = fileInfo.size.toFloat(),
        animationSpec = tween(300),
        label = "log_file_size_animation",
    )

    val sizeText = formatFileSize(if (fileInfo.isRecording) animatedSize.toLong() else fileInfo.size)
    val summary = "${dateFormat.format(Date(fileInfo.createdAt))}  ·  $sizeText"

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
            animationSpec = tween(300),
            initialOffsetY = { -it / 2 },
        ),
    ) {
        Card(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .pressable(interactionSource = interactionSource, indication = SinkFeedback())
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileInfo.name,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = summary,
                        style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (fileInfo.isRecording) {
                    Text(
                        text = MLang.Log.Status.Recording,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> String.format(Locale.getDefault(), "%.2f MB", size / (1024.0 * 1024.0))
    }
}
