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

package com.github.lmfirefly.flycat.feature.dashboard.presentation.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lmfirefly.flycat.core.util.ProxyChainResolver
import com.github.lmfirefly.flycat.feature.dashboard.presentation.viewmodel.ConnectionCardItem
import com.github.lmfirefly.flycat.presentation.component.GlassTopAction
import com.github.lmfirefly.flycat.presentation.component.GlassTopIconButton
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.misc.GroupIcon
import com.github.lmfirefly.flycat.presentation.component.misc.normalizeGroupIconUri
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.presentation.theme.LocalGlassBackdrop
import com.github.lmfirefly.flycat.presentation.theme.glassSurfaceBackdrop
import com.github.lmfirefly.flycat.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

@Composable
fun ConnectionCard(item: ConnectionCardItem, groupIcons: Map<String, String>, showCloseAction: Boolean, onClick: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = AppTheme.spacing
    val sizes = AppTheme.sizes
    val interactionSource = remember { MutableInteractionSource() }
    val network = item.network
    val chainText = item.ruleChain
    val chainNodes = remember(item.connectionInfo.rule, item.connectionInfo.chains) { ProxyChainResolver.buildRuleChain(rule = item.connectionInfo.rule, chain = item.connectionInfo.chains) }
    Card(
        modifier = modifier.fillMaxWidth().pressable(interactionSource = interactionSource, indication = SinkFeedback()).clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        insideMargin = PaddingValues(horizontal = spacing.space16, vertical = spacing.space12),
        applyHorizontalPadding = false,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.space16)) {
            ConnectionLeadingIcon(metadata = item.connectionInfo.metadata, network = network, modifier = Modifier.padding(top = spacing.space2))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space8)) {
                    Text(
                        text = item.displayHost,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).basicMarquee(),
                    )
                    Text(
                        text = item.relativeTime,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space4)) {
                    Text(
                        text = item.protocolAndNetwork,
                        style = MiuixTheme.textStyles.footnote1,
                        color = getProtocolColor(network),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.processName.isNotBlank()) {
                        Text(
                            text = item.processName,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).basicMarquee(),
                        )
                    }
                }
                if (chainNodes.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().basicMarquee().clearAndSetSemantics { contentDescription = chainText }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space4)) {
                        chainNodes.forEachIndexed { index, name ->
                            ConnectionChainIcon(name = name, groupIcons = groupIcons, width = 12.dp)
                            Text(
                                text = name,
                                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (index != chainNodes.lastIndex) {
                                Text(
                                    text = "->",
                                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(spacing.space12))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space12)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space6)) {
                TrafficMetricRow(
                    label = "↓",
                    speedText = item.downloadSpeedText,
                    totalText = item.downloadText,
                    accentColor = Color(0xFF4CAF50),
                )
                TrafficMetricRow(
                    label = "↑",
                    speedText = item.uploadSpeedText,
                    totalText = item.uploadText,
                    accentColor = Color(0xFF2196F3),
                )
            }
            if (showCloseAction) {
                Box(modifier = Modifier.heightIn(min = sizes.connectionLeadingIconSize), contentAlignment = Alignment.Center) {
                    GlassTopIconButton(
                        action = GlassTopAction(
                            icon = MiuixIcons.Glass.Delete,
                            contentDescription = FlyTxt.Connection.Detail.Action.Interrupt,
                            tint = MiuixTheme.colorScheme.error,
                            onClick = onClose,
                        ),
                        backdrop = LocalGlassBackdrop.current ?: glassSurfaceBackdrop(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ConnectionChainIcon(name: String, groupIcons: Map<String, String>, width: Dp) {
    val iconUri = remember(name, groupIcons) { groupIcons[name]?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeGroupIconUri) }
    if (iconUri != null) { GroupIcon(iconUri = iconUri, width = width) }
}

@Composable
private fun TrafficMetricRow(label: String, speedText: String, totalText: String, accentColor: Color) {
    val spacing = AppTheme.spacing
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space6)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
            color = accentColor,
            maxLines = 1,
        )
        if (speedText.isNotBlank()) {
            Text(
                text = speedText,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(spacing.space6))
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                color = accentColor,
                maxLines = 1,
            )
        }
        Text(
            text = totalText,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
