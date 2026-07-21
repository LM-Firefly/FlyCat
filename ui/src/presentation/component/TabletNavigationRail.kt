/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TabletNavigationRail(
    destinations: List<BottomBarDestination>,
    selectedDestination: BottomBarDestination,
    onDestinationClick: (BottomBarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topDestinations = destinations.filter { it != BottomBarDestination.Setting }
    val hasSettings = BottomBarDestination.Setting in destinations

    Column(
        modifier =
            modifier
                .width(UiDp.dp80)
                .fillMaxHeight()
                .background(MiuixTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = UiDp.dp8, vertical = UiDp.dp12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UiDp.dp8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            topDestinations.forEach { destination ->
                TabletNavigationRailItem(
                    destination = destination,
                    selected = destination == selectedDestination,
                    onClick = { onDestinationClick(destination) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (hasSettings) {
            TabletNavigationRailItem(
                destination = BottomBarDestination.Setting,
                selected = selectedDestination == BottomBarDestination.Setting,
                onClick = { onDestinationClick(BottomBarDestination.Setting) },
            )
        }
    }
}

@Composable
private fun TabletNavigationRailItem(
    destination: BottomBarDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val opacity = AppTheme.opacity
    val selectedColor = MiuixTheme.colorScheme.primary
    val contentColor =
        if (selected) selectedColor else MiuixTheme.colorScheme.onSurface.copy(alpha = opacity.secondaryText)
    val containerColor =
        if (selected) selectedColor.copy(alpha = opacity.subtle) else Color.Transparent

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(UiDp.dp16))
                .background(containerColor)
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(vertical = UiDp.dp10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiDp.dp4),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = contentColor,
            modifier = Modifier.size(UiDp.dp22),
        )
        Text(
            text = destination.label,
            color = contentColor,
            style = TextStyle(fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            maxLines = 1,
        )
    }
}
