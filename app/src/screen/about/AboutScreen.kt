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

@file:Suppress("FunctionName")

package com.github.yumelira.yumebox.screen.about


import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.common.util.openUrl
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.runtime.service.core.CoreProcess
import com.github.yumelira.yumebox.runtime.service.log.RuntimeLog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val AppNameGradient =
    listOf(
        Color(0xFFEFC0D9),
        Color(0xFFD2C6F0),
        Color(0xFFC0D5F5),
    )

@Composable
fun AboutScreen(navigator: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val exportLogsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                val success = exportStartupLogs(context, uri)
                withContext(Dispatchers.Main) {
                    context.toast(
                        if (success) YumeTxt.About.Support.ExportSuccess
                        else YumeTxt.About.Support.ExportFailed
                    )
                }
            }
        }
    // Core branch/hash are stamped into BuildConfig at configure time (see app/build.gradle.kts).
    val coreVersion = BuildConfig.CORE_VERSION

    Scaffold(topBar = { TopBar(title = YumeTxt.About.Title, scrollBehavior = scrollBehavior) }) {
        innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Spacer(modifier = Modifier.height(UiDp.dp24))

                Card {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth().drawBehind {
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    AppNameGradient.first().copy(alpha = 0.06f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(size.width * 0.08f, size.height * 0.1f),
                                            radius = size.width * 0.45f,
                                        )
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    AppNameGradient.last().copy(alpha = 0.06f),
                                                    Color.Transparent,
                                                ),
                                            center =
                                                Offset(size.width * 0.92f, size.height * 0.95f),
                                            radius = size.width * 0.5f,
                                        )
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = UiDp.dp64),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "YumeBox",
                                style =
                                    MiuixTheme.textStyles.title1.copy(
                                        fontSize = 44.sp,
                                        fontFamily = FontFamily.Serif,
                                        brush = Brush.linearGradient(AppNameGradient),
                                    ),
                            )

                            Spacer(modifier = Modifier.height(UiDp.dp12))

                            Text(
                                text = "${BuildConfig.VERSION_NAME} · $coreVersion",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(UiDp.dp12))

                Card {
                    BasicComponent(
                        title = "YumeBox",
                        summary = "An open-source Android client based Mihomo",
                    )
                }

                Title(YumeTxt.About.Section.ProjectLinks)
                Card {
                    AboutLinkItem(
                        title = "YumeBox",
                        url = "https://github.com/YumeYucca/YumeBox",
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

                Title(YumeTxt.About.Section.More)
                Card {
                    AboutLinkItem(
                        title = YumeTxt.About.Link.TelegramGroup,
                        url = "https://t.me/OOM_Group",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = true,
                    )
                    AboutLinkItem(
                        title = YumeTxt.About.Link.TelegramChannel,
                        url = "https://t.me/YumeYucca",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = true,
                    )
                }

                Title(YumeTxt.About.Section.Support)
                Card {
                    ArrowPreference(
                        title = YumeTxt.About.Support.ExportLogs,
                        onClick = {
                            exportLogsLauncher.launch(
                                "yumebox_runtime_${System.currentTimeMillis()}.log"
                            )
                        },
                    )
                    ArrowPreference(
                        title = YumeTxt.About.Support.ReportIssue,
                        onClick = {
                            openUrl(
                                context,
                                "https://github.com/YumeYucca/YumeBox/issues/new/choose",
                            )
                        },
                    )
                }

                Title(YumeTxt.About.Section.License)
                Card {
                    ArrowPreference(
                        title = YumeTxt.About.License.Libraries,
                        summary = YumeTxt.About.License.LibrariesSummary,
                        onClick = { navigator.push(Route.OpenSourceLicenses) },
                    )
                    BasicComponent(
                        title = YumeTxt.About.License.AgplName,
                        summary = YumeTxt.About.License.AgplDescription,
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = UiDp.dp32),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = YumeTxt.About.Copyright, style = MiuixTheme.textStyles.footnote1)
                }
                Spacer(modifier = Modifier.height(UiDp.dp32))
            }
        }
    }
}

/**
 * One plain-text file, not a zip of partial ones: the unified log already interleaves every source
 * in timestamp order, and the core's own log is appended so nothing lives outside it.
 */
private fun exportStartupLogs(context: Context, targetUri: Uri): Boolean {
    val runtimeLog = RuntimeLog.snapshot(context)
    val coreLog = runCatching { CoreProcess.coreDiagnosticLog(context) }.getOrDefault("")
    val export = buildString {
        appendLine("# YumeBox runtime log")
        appendLine("# app=${BuildConfig.VERSION_NAME} core=${BuildConfig.CORE_VERSION}")
        appendLine()
        append(runtimeLog.ifBlank { "(no runtime entries recorded)\n" })
        if (coreLog.isNotBlank()) {
            appendLine()
            appendLine("--- current core.log (verbatim) ---")
            appendLine(coreLog)
        }
    }
    return try {
        context.contentResolver.openOutputStream(targetUri)?.use { output ->
            output.write(export.toByteArray(Charsets.UTF_8))
        } ?: return false
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
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
