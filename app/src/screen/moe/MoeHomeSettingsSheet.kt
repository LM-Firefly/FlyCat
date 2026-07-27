/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

@file:Suppress("FunctionName")

package com.github.yumelira.yumebox.screen.moe


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextField

@Composable
internal fun MoeHomeSettingsSheet(
    show: Boolean,
    quote: String,
    classicHomeEnabled: Boolean,
    sidebarExpanded: Boolean,
    onQuoteChange: (String) -> Unit,
    onClassicHomeEnabledChange: (Boolean) -> Unit,
    onSidebarExpandedChange: (Boolean) -> Unit,
    onChangeWallpaper: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = AppTheme.spacing
    var draftQuote by remember(show, quote) { mutableStateOf(quote) }
    var draftClassicHomeEnabled by
    remember(show, classicHomeEnabled) {
        mutableStateOf(classicHomeEnabled)
    }
    var draftSidebarExpanded by remember(show, sidebarExpanded) { mutableStateOf(sidebarExpanded) }
    val save = {
        onQuoteChange(draftQuote)
        onClassicHomeEnabledChange(draftClassicHomeEnabled)
        onSidebarExpandedChange(draftSidebarExpanded)
        onDismiss()
    }

    AppActionBottomSheet(
        show = show,
        title = "首页设置",
        startAction = { AppBottomSheetCloseAction(onClick = onDismiss) },
        endAction = { AppBottomSheetConfirmAction(onClick = save) },
        onDismissRequest = onDismiss,
        enableNestedScroll = true,
    ) {
        LazyColumn(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = spacing.space16)) {
            item {
                TextField(
                    value = draftQuote,
                    onValueChange = { draftQuote = it },
                    label = "一言",
                    useLabelAsPlaceholder = true,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacing.space12),
                )
            }
            item {
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.space12)) {
                    Column {
                        PreferenceSwitchItem(
                            title = "回退经典首页",
                            checked = draftClassicHomeEnabled,
                            onCheckedChange = { draftClassicHomeEnabled = it },
                        )
                        PreferenceSwitchItem(
                            title = "展开侧边栏",
                            checked = draftSidebarExpanded,
                            onCheckedChange = { draftSidebarExpanded = it },
                        )
                        PreferenceValueItem(title = "更换壁纸", onClick = onChangeWallpaper)
                    }
                }
            }
        }
    }
}
