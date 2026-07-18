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

package com.github.yumelira.yumebox.feature.home.presentation.screen.moe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import com.github.yumelira.yumebox.presentation.component.AppFormDialog
import com.github.yumelira.yumebox.presentation.component.PreferenceSwitchItem
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
internal fun MoeHomeSettingsSheet(
    show: Boolean,
    quote: String,
    quoteAuthor: String,
    classicHomeEnabled: Boolean,
    homeHitokotoEnabled: Boolean,
    sidebarExpanded: Boolean,
    onQuoteChange: (String) -> Unit,
    onQuoteAuthorChange: (String) -> Unit,
    onClassicHomeEnabledChange: (Boolean) -> Unit,
    onHomeHitokotoEnabledChange: (Boolean) -> Unit,
    onSidebarExpandedChange: (Boolean) -> Unit,
    onLaunchGalleryPicker: () -> Unit,
    onNavigateToWallpaperCrop: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = AppTheme.spacing
    var draftQuote by remember(show, quote) { mutableStateOf(quote) }
    var draftQuoteAuthor by remember(show, quoteAuthor) { mutableStateOf(quoteAuthor) }
    var draftClassicHomeEnabled by remember(show, classicHomeEnabled) { mutableStateOf(classicHomeEnabled) }
    var draftHomeHitokotoEnabled by remember(show, homeHitokotoEnabled) { mutableStateOf(homeHitokotoEnabled) }
    var draftSidebarExpanded by remember(show, sidebarExpanded) { mutableStateOf(sidebarExpanded) }
    var showUrlInputDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    val saveSettings = {
        onQuoteChange(draftQuote)
        onQuoteAuthorChange(draftQuoteAuthor)
        onClassicHomeEnabledChange(draftClassicHomeEnabled)
        onHomeHitokotoEnabledChange(draftHomeHitokotoEnabled)
        onSidebarExpandedChange(draftSidebarExpanded)
        onDismiss()
    }

    AppActionBottomSheet(
        show = show,
        title = FlyTxt.AppSettings.Section.Home,
        startAction = { AppBottomSheetCloseAction(onClick = onDismiss) },
        endAction = { AppBottomSheetConfirmAction(onClick = saveSettings) },
        onDismissRequest = onDismiss,
        enableNestedScroll = true,
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space16)) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12)) {
                    Column {
                        PreferenceSwitchItem(title = FlyTxt.AppSettings.Interface.ClassicHomeTitle, checked = draftClassicHomeEnabled, onCheckedChange = { draftClassicHomeEnabled = it })
                        if (draftClassicHomeEnabled) { PreferenceSwitchItem(title = FlyTxt.AppSettings.Interface.HitokotoTitle, summary = FlyTxt.AppSettings.Interface.HitokotoSummary, checked = draftHomeHitokotoEnabled, onCheckedChange = { draftHomeHitokotoEnabled = it }) }
                        if (!draftClassicHomeEnabled) { PreferenceSwitchItem(title = FlyTxt.AppSettings.Interface.SidebarExpandedTitle, checked = draftSidebarExpanded, onCheckedChange = { draftSidebarExpanded = it }) }
                    }
                }
            }
            val showQuoteFields = !draftClassicHomeEnabled || draftHomeHitokotoEnabled
            if (showQuoteFields) {
                item { TextField(value = draftQuote, onValueChange = { draftQuote = it }, label = FlyTxt.AppSettings.Interface.HomeQuoteTitle, useLabelAsPlaceholder = true, maxLines = 2, modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12)) }
                item { TextField(value = draftQuoteAuthor, onValueChange = { draftQuoteAuthor = it }, label = FlyTxt.AppSettings.Interface.HomeQuoteAuthorTitle, useLabelAsPlaceholder = true, maxLines = 1, modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12)) }
                if (!draftClassicHomeEnabled) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12)) {
                            WindowDropdownPreference(title = FlyTxt.AppSettings.Interface.HomeWallpaperSourceTitle, summary = FlyTxt.AppSettings.Interface.HomeWallpaperSourceSummary, items = listOf(FlyTxt.AppSettings.Interface.HomeWallpaperSourceGallery, FlyTxt.AppSettings.Interface.HomeWallpaperSourceUrl), selectedIndex = -1, onSelectedIndexChange = { index -> when (index) { 0 -> onLaunchGalleryPicker(); 1 -> showUrlInputDialog = true } })
                        }
                    }
                }
            }
        }
    }

    if (showUrlInputDialog) {
        MoeHomeRemoteWallpaperUrlDialog(
            show = showUrlInputDialog,
            initialUrl = urlInput,
            onDismiss = { showUrlInputDialog = false },
            onConfirm = { url ->
                showUrlInputDialog = false
                urlInput = url
                onNavigateToWallpaperCrop(url)
            },
        )
    }
}

@Composable
private fun MoeHomeRemoteWallpaperUrlDialog(show: Boolean, initialUrl: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf(initialUrl) }
    AppFormDialog(show = show, title = FlyTxt.AppSettings.Interface.HomeWallpaperUrlDialogTitle, onDismissRequest = onDismiss, onConfirm = { val trimmed = url.trim(); if (trimmed.isNotEmpty()) onConfirm(trimmed) }, scrollable = false) {
        TextField(value = url, onValueChange = { url = it }, label = "https://example.com/image.jpg", useLabelAsPlaceholder = true, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
