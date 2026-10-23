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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.navigation.Route
import com.github.lmfirefly.flycat.presentation.theme.AnimationSpecs
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.SinkFeedback

/** 双入口日志中心（"设置 → 日志"）：一个条目进入实时 mihomo 日志预览，另一个进入应用/崩溃日志文件列表；底部另提供「日志级别」设置项。 */
@Composable
fun LogHubScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.Log.Title,
                scrollBehavior = scrollBehavior,
                navigationIcon = { NavigationBackIcon(navigator = navigator, extraStartPadding = 0.dp) },
            )
        },
    ) { innerPadding ->
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
            topPadding = 20.dp,
        ) {
            item {
                LogHubEntry(
                    index = 0,
                    title = FlyTxt.Log.Hub.MihomoTitle,
                    summary = FlyTxt.Log.Hub.MihomoSummary,
                    onClick = { navigator.push(Route.MihomoLog) },
                )
            }
            item {
                LogHubEntry(
                    index = 1,
                    title = FlyTxt.Log.Hub.AppTitle,
                    summary = FlyTxt.Log.Hub.AppSummary,
                    onClick = { navigator.push(Route.AppLog) },
                )
            }
        }
    }
}

@Composable
private fun LogHubEntry(index: Int, title: String, summary: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(AnimationSpecs.DURATION_NORMAL)) +
            slideInVertically(animationSpec = tween(AnimationSpecs.DURATION_NORMAL), initialOffsetY = { -it / 2 }),
    ) {
        Card(
            cornerRadius = AppTheme.radii.radius12,
            modifier = Modifier.padding(vertical = 4.dp)
                .pressable(interactionSource = interactionSource, indication = SinkFeedback())
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = summary,
                        style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
