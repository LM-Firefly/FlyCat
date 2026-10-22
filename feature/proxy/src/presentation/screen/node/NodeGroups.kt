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

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lmfirefly.flycat.core.model.proxy.Proxy
import com.github.lmfirefly.flycat.core.model.proxy.ProxyDisplayMode
import com.github.lmfirefly.flycat.core.model.proxy.ProxyGroupInfo
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.misc.CountryFlag
import com.github.lmfirefly.flycat.presentation.component.misc.EmojiAwareText
import com.github.lmfirefly.flycat.presentation.component.misc.GroupIcon
import com.github.lmfirefly.flycat.presentation.component.misc.normalizeGroupIconUri
import com.github.lmfirefly.flycat.presentation.component.state.LoadingDotsWave
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.Speed
import com.github.lmfirefly.flycat.presentation.icon.flycat.chevron
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.RowRevealAll
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.theme.rememberRowShown
import com.github.lmfirefly.flycat.presentation.theme.rowReveal
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

private data class GroupBadge(val label: String)

private fun groupBadge(type: String): GroupBadge = GroupBadge(type)

private fun normalizedCountryCodeOrNull(countryCode: String?): String? {
    val normalized = countryCode?.trim()?.uppercase().orEmpty()
    return normalized.takeIf { it.length == 2 && it.all(Char::isLetter) }
}

internal fun LazyListScope.nodeGroupItems(
    groups: List<ProxyGroupInfo>,
    displayMode: ProxyDisplayMode,
    onGroupClick: (ProxyGroupInfo) -> Unit,
    testingGroupNames: Set<String> = emptySet(),
    onGroupDelayTestClick: ((ProxyGroupInfo) -> Unit)? = null,
    onGroupBoundsChanged: ((String, Rect) -> Unit)? = null,
    itemVerticalPadding: Dp = UiDp.dp6,
    revealCount: State<Int> = RowRevealAll,
) {
    if (displayMode.isSingleColumn) {
        itemsIndexed(
            items = groups,
            key = { _, group -> "${group.type}:${group.name}" },
            contentType = { _, _ -> "NodeGroupCard" },
        ) { index, group ->
            NodeGroupCard(
                group = group,
                isDelayTesting = testingGroupNames.contains(group.name),
                onClick = { onGroupClick(group) },
                onTestClick = onGroupDelayTestClick,
                modifier = Modifier.rowReveal(rememberRowShown(index, revealCount)).fillMaxWidth().padding(vertical = itemVerticalPadding),
            )
        }
    } else {
        val rowCount = (groups.size + 1) / 2
        items(
            count = rowCount,
            key = { rowIndex ->
                val left = groups.getOrNull(rowIndex * 2)?.name.orEmpty()
                val right = groups.getOrNull(rowIndex * 2 + 1)?.name.orEmpty()
                "NodeGroupRow:$left:$right"
            },
            contentType = { "NodeGroupRow" },
        ) { rowIndex ->
            val left = groups.getOrNull(rowIndex * 2)
            val right = groups.getOrNull(rowIndex * 2 + 1)
            Row(
                modifier = Modifier
                    .rowReveal(rememberRowShown(rowIndex, revealCount))
                    .fillMaxWidth()
                    .padding(vertical = itemVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (left != null) {
                    NodeGroupCompactCard(
                        group = left,
                        isDelayTesting = testingGroupNames.contains(left.name),
                        onClick = { onGroupClick(left) },
                        onDelayTestClick = onGroupDelayTestClick?.let { cb -> { cb(left) } },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (right != null) {
                    NodeGroupCompactCard(
                        group = right,
                        isDelayTesting = testingGroupNames.contains(right.name),
                        onClick = { onGroupClick(right) },
                        onDelayTestClick = onGroupDelayTestClick?.let { cb -> { cb(right) } },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NodeGroupCompactCard(
    group: ProxyGroupInfo,
    isDelayTesting: Boolean,
    onClick: () -> Unit,
    onDelayTestClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(NodeCardDefaults.CornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val currentProxy = remember(group.proxies, group.now) {
        group.proxies.firstOrNull { it.name == group.now }
    }
    val currentNode = remember(currentProxy?.name, currentProxy?.title, group.now) {
        resolveProxyDisplayPresentation(
            name = currentProxy?.name ?: group.now,
            title = currentProxy?.title,
        )
    }
    val nodeName = remember(currentNode.displayName, group.now) {
        currentNode.displayName
            .ifBlank { group.now.trim() }
            .ifBlank { FlyTxt.Proxy.Mode.Direct }
    }
    val iconUri = remember(group.icon) {
        group.icon?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeGroupIconUri)
    }
    val delayLabel = nodeLatencyLabel(liveProxyDelay(currentProxy?.name, currentProxy?.delay))
    val countryCode = remember(currentNode.countryCode) {
        normalizedCountryCodeOrNull(currentNode.countryCode)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(interactionSource = interactionSource, indication = SinkFeedback())
            .clip(shape)
            .background(MiuixTheme.colorScheme.background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (iconUri != null) {
                GroupIcon(
                    iconUri = iconUri,
                    width = 20.dp,
                )
            }
            // CenterStart：内容宽文本在整行内强制靠左，避免被当成居中
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                EmojiAwareText(
                    text = group.name,
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 详细行：旗帜 + 当前节点 + 延迟（UI 仅有详细布局，无简洁分支）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                CountryFlag(
                    countryCode = countryCode,
                    width = 17.dp,
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    EmojiAwareText(
                        text = nodeName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }
            ProxyDelayIndicator(
                delayLabel = delayLabel,
                isDelayTesting = isDelayTesting,
                onDelayTestClick = onDelayTestClick,
            )
        }
    }
}

@Composable
internal fun NodeGroupCard(
    group: ProxyGroupInfo,
    isDelayTesting: Boolean,
    onClick: (ProxyGroupInfo) -> Unit,
    onTestClick: ((ProxyGroupInfo) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(AppTheme.radii.radius12)
    val interactionSource = remember { MutableInteractionSource() }
    val testInteractionSource = remember { MutableInteractionSource() }

    val proxiesByName = remember(group.proxies) { group.proxies.associateBy(Proxy::name) }
    val currentProxy = remember(group.now, proxiesByName) { proxiesByName[group.now] }
    val currentNode =
        remember(currentProxy?.name, currentProxy?.title, group.now) {
            resolveProxyDisplayPresentation(
                name = currentProxy?.name ?: group.now,
                title = currentProxy?.title,
            )
        }
    val currentNodeName =
        remember(currentNode.displayName, group.now) {
            currentNode.displayName.ifBlank { group.now.trim() }.ifBlank { FlyTxt.Proxy.Mode.Direct }
        }
    val iconUri =
        remember(group.icon) {
            group.icon?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeGroupIconUri)
        }
    val currentDelay = liveProxyDelay(currentProxy?.name, currentProxy?.delay)
    val badge = remember(group.type) { groupBadge(group.type) }
    val delayLabel = nodeLatencyLabel(currentDelay)

    Column(
        modifier =
            modifier
                .shadow(
                    elevation = UiDp.dp4,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = 0.05f),
                    spotColor = Color.Black.copy(alpha = 0.05f),
                )
                .pressable(interactionSource = interactionSource, indication = SinkFeedback())
                .clip(cardShape)
                .background(MiuixTheme.colorScheme.background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onClick(group) },
                )
                .padding(horizontal = UiDp.dp16, vertical = UiDp.dp14),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp10),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiDp.dp16),
        ) {
            if (iconUri != null) {
                GroupIcon(
                    iconUri = iconUri,
                    width = UiDp.dp44,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(UiDp.dp10),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UiDp.dp8),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            EmojiAwareText(
                                text = group.name,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        val primary = MiuixTheme.colorScheme.primary
                        Text(
                            text = badge.label,
                            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
                            color = primary,
                            modifier =
                                Modifier.clip(RoundedCornerShape(50))
                                    .background(primary.copy(alpha = 0.1f))
                                    .padding(horizontal = UiDp.dp8, vertical = UiDp.dp3),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = FlyTxt.Proxy.Node.Count.format(group.proxies.size),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = UiDp.dp8),
                        )
                        if (onTestClick != null) {
                            Box(
                                modifier =
                                    Modifier
                                        .padding(start = UiDp.dp6)
                                        .width(UiDp.dp28)
                                        .height(UiDp.dp22)
                                        .clickable(
                                            interactionSource = testInteractionSource,
                                            indication = null,
                                            enabled = !isDelayTesting,
                                            onClick = { onTestClick(group) },
                                        ),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                val primary = MiuixTheme.colorScheme.primary
                                Box(
                                    modifier =
                                        Modifier
                                            .size(UiDp.dp22)
                                            .clip(CircleShape)
                                            .background(primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isDelayTesting) {
                                        LoadingDotsWave(
                                            color = primary,
                                            modifier = Modifier.size(UiDp.dp14),
                                        )
                                    } else {
                                        Icon(
                                            FlyCat.Speed,
                                            contentDescription = null,
                                            modifier = Modifier.size(UiDp.dp14),
                                            tint = primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UiDp.dp8),
                        modifier = Modifier.weight(1f),
                    ) {
                        CountryFlag(countryCode = currentNode.countryCode, width = UiDp.dp20)
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            EmojiAwareText(
                                text = currentNodeName,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.padding(start = UiDp.dp8),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        when {
                            delayLabel != null -> {
                                val (delayText, delayColor) = delayLabel
                                Text(
                                    text = delayText,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = delayColor,
                                )
                            }

                            isDelayTesting -> {
                                LoadingDotsWave(
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(UiDp.dp14),
                                )
                            }

                            else ->
                                Icon(
                                    FlyCat.chevron,
                                    contentDescription = null,
                                    modifier = Modifier.size(UiDp.dp18),
                                    tint = AppTheme.colors.state.subtleDivider,
                                )
                        }
                    }
                }
            }
        }
    }
}
