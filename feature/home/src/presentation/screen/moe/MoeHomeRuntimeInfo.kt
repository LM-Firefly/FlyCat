/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * Copyright (c) YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 */

package com.github.yumelira.yumebox.feature.home.presentation.screen.moe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.core.util.formatBytesForDisplay
import com.github.yumelira.yumebox.presentation.component.CountryFlagCircle
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.util.extractFlaggedName
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MoeTrafficStrip(downloadSpeed: Long, uploadSpeed: Long, modifier: Modifier = Modifier) {
    Row(modifier, Arrangement.spacedBy(MoeUi.Hero.trafficRowGap), Alignment.CenterVertically) {
        Box(Modifier.weight(1f), Alignment.CenterStart) { MoeTrafficItem(FlyTxt.Home.Traffic.UpShort, uploadSpeed) }
        Box(Modifier.weight(1f), Alignment.CenterEnd) { MoeTrafficItem(FlyTxt.Home.Traffic.DownShort, downloadSpeed) }
    }
}

@Composable
private fun MoeTrafficItem(label: String, speed: Long) {
    val (value, unit) = formatBytesForDisplay(speed)
    val onSurface = MiuixTheme.colorScheme.onSurface
    Row(
        horizontalArrangement = Arrangement.spacedBy(MoeUi.Traffic.itemGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = label,
            color = onSurface.copy(alpha = 0.62f),
            style = MiuixTheme.textStyles.footnote1,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = MoeUi.Traffic.labelBottomPadding),
        )
        Text(
            text = value,
            color = onSurface,
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
        )
        Text(
            text = unit,
            color = onSurface.copy(alpha = 0.55f),
            style = MiuixTheme.textStyles.footnote1,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = MoeUi.Traffic.labelBottomPadding),
        )
    }
}

@Composable
internal fun MoeHomeInfoPanel(serverName: String?, serverPing: Int?, modifier: Modifier = Modifier) {
    val flaggedNode = remember(serverName) { serverName?.let(::extractFlaggedName) }
    val resolvedNodeName = flaggedNode?.displayName ?: serverName.orEmpty().ifBlank { "" }
    val resolvedPing =
        serverPing
            ?.takeIf { it in 1..1000 }
            ?.let { ping -> FlyTxt.Home.NodeInfo.DelayValue.format(ping) }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = MoeUi.Hero.infoRowMinHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (resolvedNodeName.isNotBlank()) {
            MoeInfoBlock(
                value = resolvedNodeName,
                modifier = Modifier.weight(1f).padding(end = MoeUi.Info.trailingPadding),
                leading = {
                    flaggedNode?.countryCode?.let { countryCode ->
                        CountryFlagCircle(countryCode = countryCode, size = AppTheme.spacing.space16)
                    }
                },
            )
        } else { Spacer(modifier = Modifier.weight(1f).padding(end = MoeUi.Info.trailingPadding)) }
        if (resolvedPing != null) {
            MoeInfoBlock(
                value = resolvedPing,
                modifier = Modifier.width(MoeUi.Hero.delayWidth),
                valueColor =
                    if (serverPing < 500) AppTheme.colors.moe.pingExcellent
                    else AppTheme.colors.moe.pingWarning,
                alignEnd = true,
            )
        }
    }
}

@Composable
private fun MoeInfoBlock(
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MiuixTheme.colorScheme.onBackground,
    valueFontFamily: FontFamily? = null,
    alignEnd: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                space = MoeUi.Info.blockGap,
                alignment = if (alignEnd) Alignment.End else Alignment.Start,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            text = value,
            color = valueColor,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            fontFamily = valueFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = if (alignEnd) Modifier else Modifier.weight(1f),
        )
    }
}
