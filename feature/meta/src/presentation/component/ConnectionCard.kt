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

package com.github.yumelira.yumebox.feature.meta.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.feature.meta.presentation.viewmodel.ConnectionCardItem
import dev.oom_wg.purejoy.mlang.MLang
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

@Composable
fun ConnectionCard(
    item: ConnectionCardItem,
    showCloseAction: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val sizes = AppTheme.sizes
    val shape = RoundedCornerShape(radii.radius24)
    val interactionSource = remember { MutableInteractionSource() }
    val network = item.network
    val chainText = item.ruleChain

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(interactionSource = interactionSource, indication = SinkFeedback())
            .clip(shape)
            .background(MiuixTheme.colorScheme.background)
            .border(sizes.nodeCardBorderWidth, MiuixTheme.colorScheme.surfaceVariant, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = spacing.space16, vertical = spacing.space12),
        verticalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.space16),
        ) {
            ConnectionLeadingIcon(
                metadata = item.connectionInfo.metadata,
                network = network,
                modifier = Modifier.padding(top = spacing.space2),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                ) {
                    Text(
                        text = item.displayHost,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(),
                    )
                    Text(
                        text = item.relativeTime,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space4),
                ) {
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
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(),
                        )
                    }
                }

                Text(
                    text = chainText,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space6),
            ) {
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
                Box(
                    modifier = Modifier.heightIn(min = sizes.connectionLeadingIconSize),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = MLang.Connection.Detail.Action.Interrupt,
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onClose)
                            .padding(horizontal = spacing.space12, vertical = spacing.space8),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficMetricRow(
    label: String,
    speedText: String,
    totalText: String,
    accentColor: Color,
) {
    val spacing = AppTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space6),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.sp),
            color = accentColor,
            maxLines = 1,
        )
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
        Text(
            text = totalText,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
