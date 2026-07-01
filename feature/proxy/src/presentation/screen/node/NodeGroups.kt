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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.github.panpf.sketch.AsyncImage as SketchAsyncImage
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.state.IntColorDrawableStateImage
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.model.ProxyDisplayMode
import com.github.yumelira.yumebox.core.model.ProxyGroupInfo
import com.github.yumelira.yumebox.presentation.component.CountryFlagCircle
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.chevron
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.UiDp
import dev.oom_wg.purejoy.mlang.MLang
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
) {
    if (displayMode.isSingleColumn) {
        items(
            items = groups,
            key = { group -> "${group.type}:${group.name}" },
            contentType = { "NodeGroupCard" },
        ) { group ->
            NodeGroupCard(
                group = group,
                isDelayTesting = testingGroupNames.contains(group.name),
                onClick = { onGroupClick(group) },
                modifier = Modifier.fillMaxWidth().padding(vertical = itemVerticalPadding),
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
                    .fillMaxWidth()
                    .padding(vertical = itemVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (left != null) {
                    NodeGroupCompactCard(
                        group = left,
                        isDelayTesting = testingGroupNames.contains(left.name),
                        showDetail = displayMode.showDetail,
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
                        showDetail = displayMode.showDetail,
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
    showDetail: Boolean,
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
            .ifBlank { MLang.Proxy.Mode.Direct }
    }
    val iconUri = remember(group.icon) {
        group.icon?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeNodeGroupIconUri)
    }
    val delayLabel = remember(currentProxy?.delay) { nodeLatencyLabel(currentProxy?.delay) }
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
                NodeGroupIcon(
                    iconUri = iconUri,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
            Text(
                text = group.name,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDetail) {
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
                    if (countryCode != null) {
                        CountryFlagCircle(
                            countryCode = countryCode,
                            size = 17.dp,
                        )
                    }
                    Text(
                        text = nodeName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                }
                ProxyDelayIndicator(
                    delayLabel = delayLabel,
                    isDelayTesting = isDelayTesting,
                    onDelayTestClick = onDelayTestClick,
                )
            }
        }
    }
}

@Composable
internal fun NodeGroupCard(
    group: ProxyGroupInfo,
    isDelayTesting: Boolean,
    onClick: (ProxyGroupInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(AppTheme.radii.radius12)
    val interactionSource = remember { MutableInteractionSource() }

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
            currentNode.displayName.ifBlank { group.now.trim() }.ifBlank { MLang.Proxy.Mode.Direct }
        }
    val iconUri =
        remember(group.icon) {
            group.icon?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeNodeGroupIconUri)
        }
    val currentDelay = remember(currentProxy) { currentProxy?.delay }
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
                NodeGroupIcon(
                    iconUri = iconUri,
                    modifier = Modifier.size(UiDp.dp44).clip(RoundedCornerShape(UiDp.dp14)),
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
                        Text(
                            text = group.name,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

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

                    Text(
                        text = MLang.Proxy.Node.Count.format(group.proxies.size),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = UiDp.dp8),
                    )
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
                        val cc = currentNode.countryCode
                        if (cc != null) {
                            CountryFlagCircle(countryCode = cc, size = UiDp.dp20)
                        }
                        Text(
                            text = currentNodeName,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
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
                                RotatingCircleGauge(
                                    isRotating = true,
                                    modifier = Modifier.size(UiDp.dp14),
                                    tint = MiuixTheme.colorScheme.primary,
                                    contentDescription = null,
                                )
                            }

                            else ->
                                Icon(
                                    Yume.chevron,
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

private fun normalizeNodeGroupIconUri(raw: String): String {
    val normalized = raw.trim()
    if (normalized.startsWith("//")) return "https:$normalized"
    if (normalized.startsWith("www.", ignoreCase = true)) return "https://$normalized"
    if (normalized.matches(Regex("^[a-zA-Z][a-zA-Z\\d+.-]*:.*$"))) return normalized
    if (normalized.matches(Regex("^[^/\\s]+\\.[^/\\s]+(?:/.*)?$"))) return "https://$normalized"
    return normalized
}

@Composable
private fun NodeGroupIcon(iconUri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val placeholderColorInt = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f).toArgb()
    val request =
        remember(context, iconUri, placeholderColorInt) {
            ImageRequest(context, iconUri) {
                placeholder(IntColorDrawableStateImage(placeholderColorInt))
                error(IntColorDrawableStateImage(placeholderColorInt))
                crossfade(true)
            }
        }
    SketchAsyncImage(
        request = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
