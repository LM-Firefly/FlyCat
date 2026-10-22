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

package com.github.lmfirefly.flycat.feature.proxy.presentation.screen.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.github.lmfirefly.flycat.feature.proxy.presentation.viewmodel.ProxyViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val NoProxyDelay = MutableStateFlow<Int?>(null)

/** 解析节点的实时延迟流；未提供时回退组快照里的 delay，行为与旧实现一致。 */
internal val LocalProxyDelayFlow = staticCompositionLocalOf<(String) -> StateFlow<Int?>> {
    { NoProxyDelay }
}

@Composable
internal fun ProvideProxyDelayFlows(viewModel: ProxyViewModel, content: @Composable () -> Unit) {
    val provider = remember(viewModel) { viewModel::proxyDelayFlow }
    CompositionLocalProvider(
        LocalProxyDelayFlow provides provider,
        content = content,
    )
}

/** 读取节点实时延迟；流中尚无值时回退 [fallback]（组快照值）。 */
@Composable
internal fun liveProxyDelay(name: String?, fallback: Int?): Int? {
    if (name == null) return fallback
    val provider = LocalProxyDelayFlow.current
    val flow = remember(name, provider) { provider(name) }
    val live by flow.collectAsState()
    return live ?: fallback
}
