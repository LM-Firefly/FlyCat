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

package com.github.yumelira.yumebox.screen.about

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.R
import com.github.yumelira.yumebox.core.bridge.Bridge
import com.github.yumelira.yumebox.platform.util.openUrl
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.DialogButtonRow
import com.github.yumelira.yumebox.presentation.component.NavigationBackIcon
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.PreferenceEnumItem
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.update.GitHubUpdateViewModel
import com.github.yumelira.yumebox.update.UpdateCandidate
import com.github.yumelira.yumebox.update.UpdateDownloadProgress
import com.github.yumelira.yumebox.update.UpdateManifestPackage
import com.github.yumelira.yumebox.update.UpdateSource
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CancellationException
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun AboutScreen(navigator: Navigator) {
    val context = LocalContext.current
    val updateViewModel = koinViewModel<GitHubUpdateViewModel>()
    val updateUiState by updateViewModel.uiState.collectAsState()
    val downloadProgress by updateViewModel.downloadProgress.collectAsState()
    var dialogCandidate by remember { mutableStateOf<UpdateCandidate?>(null) }
    val scrollBehavior = MiuixScrollBehavior()
    val coreVersion by
        produceState(initialValue = MLang.About.App.VersionLoading) {
            value =
                try {
                    Bridge.nativeCoreVersion()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    MLang.About.App.VersionFailed
                }
        }
    LaunchedEffect(updateUiState.message) {
        updateUiState.message?.let { message ->
            context.toast(message)
            updateViewModel.consumeMessage()
        }
    }
    LaunchedEffect(updateUiState.candidate) {
        updateUiState.candidate?.let {
            Timber.i("AboutScreen received update candidate: tag=%s version=%s", it.tag, it.versionName)
            dialogCandidate = it
        }
    }

    Scaffold(topBar = { TopBar(title = MLang.About.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(navigator = navigator) }) }) {
        innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(UiDp.dp24))

                    Icon(
                        painter = painterResource(id = R.drawable.yume),
                        contentDescription = "App Icon",
                        modifier = Modifier.size(UiDp.dp120).clip(RoundedCornerShape(UiDp.dp24)),
                        tint = Color.Unspecified,
                    )

                    Spacer(modifier = Modifier.height(UiDp.dp24))

                    Text(text = "FlyCat", style = MiuixTheme.textStyles.title1)

                    Spacer(modifier = Modifier.height(UiDp.dp8))

                    Text(
                        text = "${BuildConfig.VERSION_NAME} ($coreVersion)",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(UiDp.dp4))
                    Text(
                        text = "UI Build: ${BuildConfig.UI_BUILD_ID.ifBlank { "000000000000-000000" }}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )

                    Spacer(modifier = Modifier.height(UiDp.dp32))
                }

                Card {
                    BasicComponent(
                        title = "FlyCat",
                        summary = "An open-source Android client based Mihomo",
                    )
                    PreferenceEnumItem(
                        title = MLang.AppSettings.Network.UpdateChannelTitle,
                        summary = MLang.AppSettings.Network.UpdateChannelSummary,
                        currentValue = updateUiState.source,
                        items = listOf(
                            MLang.AppSettings.Network.UpdateChannelStable,
                            MLang.AppSettings.Network.UpdateChannelPre,
                            MLang.AppSettings.Network.UpdateChannelSmart,
                        ),
                        values = listOf(
                            UpdateSource.Latest,
                            UpdateSource.Prerelease,
                            UpdateSource.Smart,
                        ),
                        onValueChange = updateViewModel::setSource,
                    )
                    ArrowPreference(
                        title = MLang.About.License.CheckUpdate,
                        summary = if (updateUiState.isChecking) {
                            MLang.Component.Update.Message.Checking
                        } else {
                            MLang.About.License.CheckUpdateSummary
                        },
                        enabled = !updateUiState.isChecking && !downloadProgress.isDownloading,
                        onClick = updateViewModel::checkForUpdate,
                    )
                }

                Title(MLang.About.Section.ProjectLinks)
                Card {
                    AboutLinkItem(
                        title = "FlyCat",
                        url = "https://github.com/LM-Firefly/FlyCat",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = false,
                    )
                    AboutLinkItem(
                        title = "Mihomo",
                        url = "https://github.com/MetaCubeX/mihomo",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = false,
                    )
                }

                Title(MLang.About.Section.License)
                Card {
                    ArrowPreference(
                        title = MLang.About.License.Libraries,
                        summary = MLang.About.License.LibrariesSummary,
                        onClick = { navigator.push(Route.OpenSourceLicenses) },
                    )
                    BasicComponent(
                        title = MLang.About.License.AgplName,
                        summary = MLang.About.License.AgplDescription,
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = UiDp.dp32),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = MLang.About.Copyright, style = MiuixTheme.textStyles.footnote1)
                }
                Spacer(modifier = Modifier.height(UiDp.dp32))
            }
        }
    }
    UpdateCandidateDialog(
        candidate = dialogCandidate,
        downloadProgress = downloadProgress,
        onDismiss = {
            dialogCandidate = null
            updateViewModel.dismissCandidate()
        },
        onDownload = updateViewModel::downloadAndInstall,
        onCancelDownload = updateViewModel::cancelDownload,
    )
}

@Composable
private fun AboutLinkItem(
    title: String,
    url: String,
    onOpenUrl: (String) -> Unit,
    showArrow: Boolean,
) {
    if (showArrow) {
        ArrowPreference(title = title, summary = url, onClick = { onOpenUrl(url) })
    } else {
        BasicComponent(title = title, summary = url, onClick = { onOpenUrl(url) })
    }
}

@Composable
private fun UpdateCandidateDialog(
    candidate: UpdateCandidate?,
    downloadProgress: UpdateDownloadProgress,
    onDismiss: () -> Unit,
    onDownload: (UpdateCandidate, UpdateManifestPackage?) -> Unit,
    onCancelDownload: () -> Unit,
) {
    if (candidate == null) return
    val packageOptions = remember(candidate) { candidate.selectablePackages() }
    var selectedPackage by remember(candidate) { mutableStateOf(packageOptions.firstOrNull()) }
    val releaseNotes = candidate.releaseNotes.ifBlank { MLang.Component.Update.Message.Available }
    val message = buildString {
        appendLine("${MLang.Component.Update.Message.CurrentVersion}: ${BuildConfig.VERSION_NAME}")
        appendLine("${MLang.Component.Update.Message.RemoteVersion}: ${candidate.versionName}")
        if (candidate.tag.isNotBlank()) {
            appendLine("${MLang.Component.Update.Message.Tag}: ${candidate.tag}")
        }
        appendLine()
        append(releaseNotes)
    }
    WindowDialog(
        show = true,
        title = MLang.Component.Update.Title.Available,
        titleColor = DialogDefaults.titleColor(),
        summary = null,
        summaryColor = DialogDefaults.summaryColor(),
        backgroundColor = DialogDefaults.backgroundColor(),
        enableWindowDim = true,
        onDismissRequest = {
            if (downloadProgress.isDownloading) {
                onCancelDownload()
            } else {
                onDismiss()
            }
        },
        outsideMargin = DialogDefaults.outsideMargin,
        insideMargin = DialogDefaults.insideMargin,
        defaultWindowInsetsPadding = true,
    ) {
        if (downloadProgress.isDownloading) {
            UpdateDownloadContent(progress = downloadProgress, onCancelDownload = onCancelDownload)
        } else {
            val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.75).dp
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxDialogHeight),
            ) {
                // Fixed header
                UpdateDialogMessage(message)
                if (packageOptions.size > 1) {
                    Spacer(modifier = Modifier.height(UiDp.dp16))
                    Text(text = MLang.Component.Update.Message.SelectPackage, style = MiuixTheme.textStyles.body1)
                    Spacer(modifier = Modifier.height(UiDp.dp8))
                }
                // Scrollable package list
                if (packageOptions.size > 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        packageOptions.forEach { packageOption ->
                            val isSelected = selectedPackage == packageOption
                            BasicComponent(
                                title = packageOption.packageDisplayTitle(),
                                summary = packageOption.packageDisplaySummary(),
                                endActions = {
                                    Checkbox(
                                        state = ToggleableState(isSelected),
                                        onClick = { selectedPackage = packageOption },
                                    )
                                },
                                onClick = { selectedPackage = packageOption },
                            )
                        }
                    }
                }
                // Fixed buttons
                Spacer(modifier = Modifier.height(UiDp.dp12))
                DialogButtonRow(
                    onCancel = onDismiss,
                    onConfirm = { onDownload(candidate, selectedPackage) },
                    confirmText = MLang.Component.Update.Action.DownloadNow,
                )
            }
        }
    }
}

private fun UpdateCandidate.selectablePackages(): List<UpdateManifestPackage> {
    val available = manifest.packages.filter {
        it.downloadUrl.isNotBlank() &&
            !it.fileName.contains("standalone", ignoreCase = true) &&
            !it.fileName.contains("lite", ignoreCase = true)
    }
    if (available.isEmpty()) return emptyList()
    val supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
    val exactMatches = supportedAbis.flatMap { abi ->
        available.filter { it.abi.equals(abi, ignoreCase = true) }
    }
    val universalMatches = available.filter {
        it.isUniversal || it.abi.equals("universal", ignoreCase = true)
    }
    val compatible = (exactMatches + universalMatches)
        .distinctBy { it.fileName.ifBlank { it.downloadUrl } }
    return if (compatible.isNotEmpty()) compatible else available.distinctBy { it.fileName.ifBlank { it.downloadUrl } }
}

private fun UpdateManifestPackage.packageDisplayTitle(): String {
    val abiLabel = abi.ifBlank { MLang.Component.Update.Message.PackageUniversal }
    val variant = when {
        fileName.contains("extension", ignoreCase = true) -> MLang.Component.Update.Message.PackageExtension
        else -> MLang.Component.Update.Message.PackageStandard
    }
    return "$variant / $abiLabel"
}

private fun UpdateManifestPackage.packageDisplaySummary(): String {
    val sizeMb = if (size > 0) String.format(java.util.Locale.US, "%.1f MB", size / 1024f / 1024f) else ""
    return listOf(fileName, sizeMb)
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

@Composable
private fun UpdateDownloadContent(progress: UpdateDownloadProgress, onCancelDownload: () -> Unit) {
    val progressValue = (progress.progress.coerceIn(0, 100) / 100f)
    Column {
        UpdateDialogMessage(progress.message.ifBlank { MLang.Component.Update.Message.Downloading })
        Spacer(modifier = Modifier.height(UiDp.dp12))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(UiDp.dp8)
                .clip(RoundedCornerShape(UiDp.dp100))
                .background(MiuixTheme.colorScheme.secondaryVariant.copy(alpha = 0.24f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressValue)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(UiDp.dp100))
                    .background(MiuixTheme.colorScheme.primary),
            )
        }
        Spacer(modifier = Modifier.height(UiDp.dp12))
        Button(
            onClick = onCancelDownload,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiDp.dp8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = MLang.Component.Button.Cancel,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun UpdateDialogMessage(message: String) {
    Text(
        text = message,
        style = MiuixTheme.textStyles.body1,
    )
}
