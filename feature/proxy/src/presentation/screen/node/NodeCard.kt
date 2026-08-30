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

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.lmfirefly.flycat.core.model.proxy.Proxy
import com.github.lmfirefly.flycat.feature.proxy.presentation.util.extractNodeTags
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.misc.CountryFlagCircle
import com.github.lmfirefly.flycat.presentation.component.state.LoadingDotsWave
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.BadgeDollarSign
import com.github.lmfirefly.flycat.presentation.icon.flycat.Cloud
import com.github.lmfirefly.flycat.presentation.icon.flycat.Tags
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

internal object NodeCardDefaults {
    val CornerRadius = 12.dp
    val GroupCornerRadius = 12.dp
    val PaddingHorizontal = 12.dp
    val PaddingVertical = 16.dp
}

@Composable
internal fun nodeLatencyLabel(delay: Int?, withUnit: Boolean = false): Pair<String, Color>? {
    if (delay == null || delay == 0) return null
    val text = if (delay < 0) {
        FlyTxt.Proxy.Node.Timeout
    } else if (withUnit) {
        FlyTxt.Home.NodeInfo.DelayValue.format(delay)
    } else {
        delay.toString()
    }
    val color = when {
        delay < 0 -> Color(0xFF9E9E9E)
        delay in 1..500 -> AppTheme.colors.latency.fast
        delay in 501..1000 -> AppTheme.colors.latency.moderate
        delay in 1001..3000 -> AppTheme.colors.latency.slow
        delay in 3001..5000 -> AppTheme.colors.latency.terrible
        else -> return null
    }
    return text to color
}

internal fun displayName(type: String): String =
    when (type) {
        Proxy.Type.Shadowsocks -> "SS"
        Proxy.Type.ShadowsocksR -> "SSR"
        Proxy.Type.Socks5 -> "SOCKS5"
        Proxy.Type.Http -> "HTTP"
        Proxy.Type.Vmess -> "VMess"
        Proxy.Type.Vless -> "VLESS"
        Proxy.Type.Tuic -> "TUIC"
        Proxy.Type.Dns -> "DNS"
        Proxy.Type.Ssh -> "SSH"
        else -> type
    }

internal fun iconLabel(type: String): String =
    when (type) {
        Proxy.Type.Direct -> "DI"
        Proxy.Type.Reject -> "RJ"
        Proxy.Type.RejectDrop -> "RD"
        Proxy.Type.Compatible -> "CP"
        Proxy.Type.Pass -> "PS"
        Proxy.Type.PassRule -> "PR"
        Proxy.Type.Relay -> "RL"
        Proxy.Type.Selector -> "SE"
        Proxy.Type.Fallback -> "FB"
        Proxy.Type.URLTest -> "UT"
        Proxy.Type.LoadBalance -> "LB"
        Proxy.Type.Smart -> "SM"
        Proxy.Type.Unknown -> "UN"
        Proxy.Type.Shadowsocks -> "SS"
        Proxy.Type.ShadowsocksR -> "SR"
        Proxy.Type.Snell -> "SN"
        Proxy.Type.Socks5 -> "S5"
        Proxy.Type.Http -> "HT"
        Proxy.Type.Vmess -> "VM"
        Proxy.Type.Vless -> "VL"
        Proxy.Type.Trojan -> "TR"
        Proxy.Type.Hysteria -> "HY"
        Proxy.Type.Hysteria2 -> "H2"
        Proxy.Type.Tuic -> "TU"
        Proxy.Type.WireGuard -> "WG"
        Proxy.Type.Dns -> "DN"
        Proxy.Type.Ssh -> "SH"
        Proxy.Type.Mieru -> "MI"
        Proxy.Type.AnyTLS -> "AT"
        Proxy.Type.Sudoku -> "SU"
        Proxy.Type.Masque -> "MQ"
        Proxy.Type.TrustTunnel -> "TT"
        Proxy.Type.OpenVPN -> "OP"
        Proxy.Type.Tailscale -> "Tl"
        Proxy.Type.GostRelay -> "GR"
        else -> type.take(2).uppercase()
    }

@Composable
internal fun NodeSelectableCard(
    isSelected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    paddingVertical: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val radii = AppTheme.radii
    val sizes = AppTheme.sizes
    val opacity = AppTheme.opacity
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(radii.radius12)
    val primary = MiuixTheme.colorScheme.primary
    val backgroundColor = if (isSelected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MiuixTheme.colorScheme.background
    }
    val transition = updateTransition(targetState = isSelected, label = "node_card_selection")
    val borderColor by
        transition.animateColor(
            transitionSpec = { tween(durationMillis = 220, easing = FastOutSlowInEasing) },
            label = "node_card_border_color",
        ) { selected ->
            if (selected) primary.copy(alpha = opacity.disabled) else Color.Transparent
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .let {
                    if (onClick != null) {
                        it.pressable(
                            interactionSource = interactionSource,
                            indication = SinkFeedback(),
                        )
                    } else {
                        it
                    }
                }
                .clip(shape)
                .background(backgroundColor)
                .border(sizes.nodeCardBorderWidth, borderColor, shape)
                .let {
                    if (onClick != null) {
                        it.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        it
                    }
                }
                .padding(horizontal = sizes.nodeCardPaddingHorizontal, vertical = paddingVertical),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NodeCard(
    proxy: Proxy,
    isSelected: Boolean,
    isPinned: Boolean = false,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    isDelayTesting: Boolean = false,
    isThisProxyTesting: Boolean = false,
    onSingleNodeTestClick: ((String) -> Unit)? = null,
    isSingleColumn: Boolean = true,
    showDetail: Boolean = true,
    showCountryFlag: Boolean = true,
    resolvedChildNodeName: String? = null,
) {
    val sizes = AppTheme.sizes
    val onCardClick =
        remember(proxy.name, onClick) { onClick?.let { click -> { click(proxy.name) } } }
    val onNodeTestClick =
        remember(proxy.name, onSingleNodeTestClick) {
            onSingleNodeTestClick?.let { click -> { click(proxy.name) } }
        }

    NodeSelectableCard(
        isSelected = isSelected,
        onClick = onCardClick,
        modifier = modifier,
        paddingVertical = sizes.nodeCardPaddingVertical,
    ) {
        val tags = remember(proxy.name) { extractNodeTags(proxy.name) }
        val delayLabel = nodeLatencyLabel(proxy.delay, withUnit = isSingleColumn)
        val childNodeName = remember(resolvedChildNodeName, proxy.name) {
            val raw = resolvedChildNodeName?.trim().orEmpty()
            raw.takeIf { it.isNotEmpty() && it != proxy.name.trim() }
        }
        val textColor = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
        if (showDetail) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = proxy.name,
                        style = MiuixTheme.textStyles.body2,
                        color = textColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isSingleColumn) {
                            Modifier.basicMarquee()
                        } else {
                            Modifier.weight(1f).basicMarquee()
                        },
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NodeTagChip(label = displayName(proxy.type))
                        tags.keywords.forEach { keyword -> NodeTagChip(label = keyword) }
                        tags.multiplier?.let { multiplier ->
                            if (multiplier > 0f) NodeMultiplierChip(multiplier = multiplier)
                        }
                        if (childNodeName != null) {
                            Text(
                                text = childNodeName,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).basicMarquee(),
                            )
                        }
                    }
                    ProxyDelayIndicator(
                        delayLabel = delayLabel,
                        isDelayTesting = isDelayTesting || isThisProxyTesting,
                        onDelayTestClick = onNodeTestClick,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = proxy.name,
                    style = MiuixTheme.textStyles.body2,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).basicMarquee(),
                )
            }
        }
        if (isPinned) {
            Icon(
                imageVector = FlyCat.Tags,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-8).dp)
                    .size(18.dp),
            )
        }
    }
}

@Composable
internal fun ProxyDelayIndicator(
    delayLabel: Pair<String, Color>?,
    isDelayTesting: Boolean,
    onDelayTestClick: (() -> Unit)?,
) {
    val slotModifier = Modifier.widthIn(min = 20.dp)
    when {
        isDelayTesting -> {
            Box(modifier = slotModifier, contentAlignment = Alignment.CenterEnd) {
                LoadingDotsWave(
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        delayLabel != null -> {
            val (delayText, delayColor) = delayLabel
            Text(
                text = delayText,
                style = MiuixTheme.textStyles.footnote1,
                color = delayColor,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .let { m ->
                        if (onDelayTestClick != null) m.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDelayTestClick,
                        ) else m
                    },
            )
        }
        else -> {
            Icon(
                imageVector = FlyCat.Cloud,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .let { m ->
                        if (onDelayTestClick != null) m.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDelayTestClick,
                        ) else m
                    },
                tint = Color(0xFFC7C7CC),
            )
        }
    }
}

@Composable
internal fun NodeLargeIcon(modifier: Modifier = Modifier, countryCode: String?, typeName: String) {
    val opacity = AppTheme.opacity
    val sizes = AppTheme.sizes
    val neutral = MiuixTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .size(sizes.nodeLargeIconSize)
                .clip(RoundedCornerShape(sizes.nodeLargeIconCornerRadius))
                .background(neutral.copy(alpha = opacity.ambientLight + opacity.ambientShadow)),
        contentAlignment = Alignment.Center,
    ) {
        if (countryCode != null) {
            CountryFlagCircle(countryCode = countryCode, size = sizes.nodeLargeIconFlagSize)
        } else {
            Text(
                text = typeName.take(2),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun NodeTagChip(label: String) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val opacity = AppTheme.opacity
    val primary = MiuixTheme.colorScheme.primary
    Text(
        text = label,
        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
        color = primary,
        modifier =
            Modifier.clip(RoundedCornerShape(radii.full))
                .background(primary.copy(alpha = opacity.subtle))
                .padding(horizontal = spacing.space4, vertical = spacing.space2),
    )
}

@Composable
private fun NodeMultiplierChip(multiplier: Float) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val opacity = AppTheme.opacity
    val appColors = AppTheme.colors
    val sizes = AppTheme.sizes
    val isHigh = multiplier >= 2.0f
    val primary = MiuixTheme.colorScheme.primary
    val chipBg =
        if (isHigh) appColors.status.destructiveContainer else primary.copy(alpha = opacity.subtle)
    val chipColor = if (isHigh) appColors.status.destructive else primary
    val label =
        if (multiplier == multiplier.toLong().toFloat()) {
            "x${multiplier.toLong()}"
        } else {
            "x$multiplier"
        }

    Row(
        modifier =
            Modifier.clip(RoundedCornerShape(radii.full))
                .background(chipBg)
                .padding(horizontal = spacing.space4, vertical = spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space2),
    ) {
        Icon(
            imageVector = FlyCat.BadgeDollarSign,
            contentDescription = null,
            tint = chipColor,
            modifier = Modifier.size(sizes.nodeTagIconSize),
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 10.sp),
            color = chipColor,
        )
    }
}
