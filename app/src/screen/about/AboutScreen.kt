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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.common.util.openUrl
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.theme.UiDp
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CancellationException
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

// Fault barrier at the JNI bridge: any native failure degrades to a fallback version label.
@Suppress("TooGenericExceptionCaught")
private fun loadCoreVersionOrFallback(): String =
    // The core runs out-of-process; its exact version is available at runtime via the REST /version
    // endpoint. The static About screen shows the tracked core name.
    "mihomo"

@Composable
fun AboutScreen(navigator: Navigator) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val coreVersion by
        produceState(initialValue = MLang.About.App.VersionLoading) {
            value = loadCoreVersionOrFallback()
        }

    Scaffold(topBar = { TopBar(title = MLang.About.Title, scrollBehavior = scrollBehavior) }) {
        innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Spacer(modifier = Modifier.height(UiDp.dp24))

                val ringColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.10f)
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
                                val ringStroke =
                                    Stroke(
                                        width = 1.dp.toPx(),
                                        pathEffect =
                                            PathEffect.dashPathEffect(
                                                floatArrayOf(2.dp.toPx(), 7.dp.toPx()),
                                            ),
                                        cap = StrokeCap.Round,
                                    )
                                drawCircle(
                                    color = ringColor,
                                    radius = size.width * 0.55f,
                                    center = Offset(size.width * 0.2f, -size.height * 0.7f),
                                    style = ringStroke,
                                )
                                drawCircle(
                                    color = ringColor,
                                    radius = size.width * 0.6f,
                                    center = Offset(size.width * 0.85f, size.height * 1.8f),
                                    style = ringStroke,
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

                Title(MLang.About.Section.ProjectLinks)
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

                Title(MLang.About.Section.More)
                Card {
                    AboutLinkItem(
                        title = MLang.About.Link.TelegramGroup,
                        url = "https://t.me/OOM_Group",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = true,
                    )
                    AboutLinkItem(
                        title = MLang.About.Link.TelegramChannel,
                        url = "https://t.me/YumeLira",
                        onOpenUrl = { url -> openUrl(context, url) },
                        showArrow = true,
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
