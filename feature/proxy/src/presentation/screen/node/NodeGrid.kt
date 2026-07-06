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

package com.github.yumelira.yumebox.feature.proxy.presentation.screen.node

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.model.ProxyDisplayMode
import com.github.yumelira.yumebox.presentation.theme.UiDp
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

internal fun LazyListScope.nodeGridItems(
    proxies: List<Proxy>,
    selectedProxyName: String,
    pinnedProxyName: String = "",
    displayMode: ProxyDisplayMode,
    onProxyClick: ((String) -> Unit)? = null,
    isDelayTesting: Boolean = false,
    testingProxyNames: Set<String> = emptySet(),
    onSingleNodeTestClick: ((String) -> Unit)? = null,
    resolveChildNodeName: ((Proxy) -> String?)? = null,
    outerHorizontalPadding: Dp = UiDp.dp0,
    itemVerticalPadding: Dp = UiDp.dp0,
    singleNodeTestEnabled: Boolean = true,
) {
    val showDetail = displayMode.showDetail
    val isSingleColumn = displayMode.isSingleColumn
    if (isSingleColumn) {
        items(items = proxies, key = { it.name }, contentType = { "NodeCard1" }) { proxy ->
            NodeCard(
                proxy = proxy,
                isSelected = proxy.name == selectedProxyName,
                isPinned = proxy.name == pinnedProxyName,
                onClick = onProxyClick,
                isDelayTesting = isDelayTesting,
                isThisProxyTesting = proxy.name in testingProxyNames,
                onSingleNodeTestClick = onSingleNodeTestClick?.let { { it(proxy.name) } },
                isSingleColumn = true,
                showDetail = showDetail,
                showCountryFlag = true,
                resolvedChildNodeName = resolveChildNodeName?.invoke(proxy),
                singleNodeTestEnabled = singleNodeTestEnabled,
                modifier =
                    Modifier.animateItem()
                        .padding(
                            horizontal = outerHorizontalPadding,
                            vertical = itemVerticalPadding,
                        ),
            )
        }
        return
    }
    val rowCount = (proxies.size + 1) / 2
    items(
        count = rowCount,
        key = { rowIndex ->
            val i = rowIndex * 2
            val left = proxies.getOrNull(i)?.name.orEmpty()
            val right = proxies.getOrNull(i + 1)?.name.orEmpty()
            "$left|$right"
        },
        contentType = { "ProxyRow2" },
    ) { rowIndex ->
        val i = rowIndex * 2
        val left = proxies.getOrNull(i)
        val right = proxies.getOrNull(i + 1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = outerHorizontalPadding, vertical = itemVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (left != null) {
                NodeCard(
                    proxy = left,
                    isSelected = left.name == selectedProxyName,
                    isPinned = left.name == pinnedProxyName,
                    onClick = onProxyClick,
                    isDelayTesting = isDelayTesting,
                    isThisProxyTesting = left.name in testingProxyNames,
                    onSingleNodeTestClick = onSingleNodeTestClick?.let { { it(left.name) } },
                    isSingleColumn = false,
                    showDetail = showDetail,
                    showCountryFlag = true,
                    resolvedChildNodeName = resolveChildNodeName?.invoke(left),
                    singleNodeTestEnabled = singleNodeTestEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .animateItem(),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (right != null) {
                NodeCard(
                    proxy = right,
                    isSelected = right.name == selectedProxyName,
                    isPinned = right.name == pinnedProxyName,
                    onClick = onProxyClick,
                    isDelayTesting = isDelayTesting,
                    isThisProxyTesting = right.name in testingProxyNames,
                    onSingleNodeTestClick = onSingleNodeTestClick?.let { { it(right.name) } },
                    isSingleColumn = false,
                    showDetail = showDetail,
                    showCountryFlag = true,
                    resolvedChildNodeName = resolveChildNodeName?.invoke(right),
                    singleNodeTestEnabled = singleNodeTestEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .animateItem(),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun NodeGrid(
    proxies: List<Proxy>,
    selectedProxyName: String,
    pinnedProxyName: String = "",
    displayMode: ProxyDisplayMode,
    onProxyClick: ((String) -> Unit)? = null,
    isDelayTesting: Boolean = false,
    testingProxyNames: Set<String> = emptySet(),
    onSingleNodeTestClick: ((String) -> Unit)? = null,
    resolveChildNodeName: ((Proxy) -> String?)? = null,
    listStateKey: String? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(UiDp.dp0),
    singleNodeTestEnabled: Boolean = true,
) {
    val listState = rememberSaveable(listStateKey, saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(
        modifier = modifier.scrollEndHaptic().overScrollVertical(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(UiDp.dp12),
        overscrollEffect = null,
    ) {
        nodeGridItems(
            proxies = proxies,
            selectedProxyName = selectedProxyName,
            pinnedProxyName = pinnedProxyName,
            displayMode = displayMode,
            onProxyClick = onProxyClick,
            isDelayTesting = isDelayTesting,
            testingProxyNames = testingProxyNames,
            onSingleNodeTestClick = onSingleNodeTestClick,
            resolveChildNodeName = resolveChildNodeName,
            singleNodeTestEnabled = singleNodeTestEnabled,
        )
    }
}
