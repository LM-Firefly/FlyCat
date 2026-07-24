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
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.common.util.openUrl
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.theme.UiDp
import tf.gal.yumebox.locale.YumeTxt
import com.github.yumelira.yumebox.runtime.service.session.RuntimeStartupLogStore
import com.github.yumelira.yumebox.runtime.service.core.CoreProcess
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            contract = ActivityResultContracts.CreateDocument("application/zip")
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
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    AppNameGradient.last().copy(alpha = 0.06f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(size.width * 0.92f, size.height * 0.95f),
                                            radius = size.width * 0.5f,
                                        ),
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
                            exportLogsLauncher.launch("yumebox_startup_logs_${System.currentTimeMillis()}.zip")
                        },
                    )
                    ArrowPreference(
                        title = YumeTxt.About.Support.ReportIssue,
                        onClick = {
                            openUrl(context, "https://github.com/YumeYucca/YumeBox/issues/new/choose")
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

private fun exportStartupLogs(context: Context, targetUri: Uri): Boolean {
    val logs =
        listOf(
            RuntimeStartupLogStore.Scope.ROOT_TUN to
                RuntimeStartupLogStore(context, RuntimeStartupLogStore.Scope.ROOT_TUN).snapshot(),
            RuntimeStartupLogStore.Scope.LOCAL_TUN to
                RuntimeStartupLogStore(context, RuntimeStartupLogStore.Scope.LOCAL_TUN).snapshot(),
        )
    val coreDiagnostics =
        runCatching {
                CoreProcess.coreDiagnosticLog(context)
            }
            .getOrDefault("")
    return try {
        context.contentResolver.openOutputStream(targetUri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                logs.forEach { (scope, content) ->
                    zip.putNextEntry(ZipEntry(scope.fileName))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(CORE_DIAGNOSTICS_EXPORT_NAME))
                zip.write(coreDiagnostics.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        } ?: return false
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private const val CORE_DIAGNOSTICS_EXPORT_NAME = "core_diagnostics.log"

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
