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

package com.github.lmfirefly.flycat.feature.home.presentation.screen.moe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetCloseAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppBottomSheetConfirmAction
import com.github.lmfirefly.flycat.presentation.component.dialog.AppFormDialog
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownOption
import com.github.lmfirefly.flycat.presentation.component.GlassDropdownPreference
import com.github.lmfirefly.flycat.presentation.component.input.GlassTextField
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceSwitchItem
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.theme.AppTheme

@Composable
internal fun MoeHomeSettingsSheet(
    show: Boolean,
    quote: String,
    quoteAuthor: String,
    classicHomeEnabled: Boolean,
    homeHitokotoEnabled: Boolean,
    sidebarExpanded: Boolean,
    wallpaperScrimEnabled: Boolean,
    onQuoteChange: (String) -> Unit,
    onQuoteAuthorChange: (String) -> Unit,
    onClassicHomeEnabledChange: (Boolean) -> Unit,
    onHomeHitokotoEnabledChange: (Boolean) -> Unit,
    onSidebarExpandedChange: (Boolean) -> Unit,
    onWallpaperScrimEnabledChange: (Boolean) -> Unit,
    onLaunchGalleryPicker: () -> Unit,
    onNavigateToWallpaperCrop: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = AppTheme.spacing
    val draftQuote = rememberTextFieldState(quote)
    val draftQuoteAuthor = rememberTextFieldState(quoteAuthor)
    var draftClassicHomeEnabled by remember(show, classicHomeEnabled) { mutableStateOf(classicHomeEnabled) }
    var draftHomeHitokotoEnabled by remember(show, homeHitokotoEnabled) { mutableStateOf(homeHitokotoEnabled) }
    var draftSidebarExpanded by remember(show, sidebarExpanded) { mutableStateOf(sidebarExpanded) }
    var draftWallpaperScrimEnabled by remember(show, wallpaperScrimEnabled) { mutableStateOf(wallpaperScrimEnabled) }
    var showUrlInputDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    LaunchedEffect(show) {
        if (show) {
            draftQuote.setTextAndPlaceCursorAtEnd(quote)
            draftQuoteAuthor.setTextAndPlaceCursorAtEnd(quoteAuthor)
        }
    }
    LaunchedEffect(quote, quoteAuthor) {
        if (show && draftQuote.text.toString() != quote) {
            draftQuote.setTextAndPlaceCursorAtEnd(quote)
        }
        if (show && draftQuoteAuthor.text.toString() != quoteAuthor) {
            draftQuoteAuthor.setTextAndPlaceCursorAtEnd(quoteAuthor)
        }
    }
    val saveSettings = {
        onQuoteChange(draftQuote.text.toString())
        onQuoteAuthorChange(draftQuoteAuthor.text.toString())
        onClassicHomeEnabledChange(draftClassicHomeEnabled)
        onHomeHitokotoEnabledChange(draftHomeHitokotoEnabled)
        onSidebarExpandedChange(draftSidebarExpanded)
        onWallpaperScrimEnabledChange(draftWallpaperScrimEnabled)
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
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12), applyHorizontalPadding = false) {
                    Column {
                        PreferenceSwitchItem(title = FlyTxt.AppSettings.Interface.ClassicHomeTitle, checked = draftClassicHomeEnabled, onCheckedChange = { draftClassicHomeEnabled = it })
                        if (draftClassicHomeEnabled) { PreferenceSwitchItem(title = FlyTxt.AppSettings.Interface.HitokotoTitle, summary = FlyTxt.AppSettings.Interface.HitokotoSummary, checked = draftHomeHitokotoEnabled, onCheckedChange = { draftHomeHitokotoEnabled = it }) }
                        if (!draftClassicHomeEnabled) { PreferenceSwitchItem(title = FlyTxt.AppSettings.Interface.SidebarExpandedTitle, checked = draftSidebarExpanded, onCheckedChange = { draftSidebarExpanded = it }) }
                        if (!draftClassicHomeEnabled) { PreferenceSwitchItem(title = FlyTxt.AppSettings.Interface.WallpaperScrimTitle, checked = draftWallpaperScrimEnabled, onCheckedChange = { draftWallpaperScrimEnabled = it }) }
                    }
                }
            }
            val showQuoteFields = !draftClassicHomeEnabled || draftHomeHitokotoEnabled
            if (showQuoteFields) {
                item { GlassTextField(state = draftQuote, label = FlyTxt.AppSettings.Interface.HomeQuoteTitle, useLabelAsPlaceholder = true, lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 2), modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12)) }
                item { GlassTextField(state = draftQuoteAuthor, label = FlyTxt.AppSettings.Interface.HomeQuoteAuthorTitle, useLabelAsPlaceholder = true, lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 1), modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12)) }
                if (!draftClassicHomeEnabled) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space12), applyHorizontalPadding = false) {
                            GlassDropdownPreference(
                                title = FlyTxt.AppSettings.Interface.HomeWallpaperSourceTitle,
                                summary = FlyTxt.AppSettings.Interface.HomeWallpaperSourceSummary,
                                options = listOf(
                                    GlassDropdownOption(value = 0, label = FlyTxt.AppSettings.Interface.HomeWallpaperSourceGallery),
                                    GlassDropdownOption(value = 1, label = FlyTxt.AppSettings.Interface.HomeWallpaperSourceUrl)
                                ),
                                selected = null,
                                onSelected = {
                                    index -> when (index) {
                                        0 -> onLaunchGalleryPicker();
                                        1 -> showUrlInputDialog = true
                                    }
                                }
                            )
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
    val url = rememberTextFieldState(initialUrl)
    LaunchedEffect(initialUrl) { if (url.text.toString() != initialUrl) { url.setTextAndPlaceCursorAtEnd(initialUrl) } }
    AppFormDialog(show = show, title = FlyTxt.AppSettings.Interface.HomeWallpaperUrlDialogTitle, onDismissRequest = onDismiss, onConfirm = { val trimmed = url.text.toString().trim(); if (trimmed.isNotEmpty()) onConfirm(trimmed) }, scrollable = false) {
        GlassTextField(state = url, label = "https://example.com/image.jpg", useLabelAsPlaceholder = true, lineLimits = TextFieldLineLimits.SingleLine, modifier = Modifier.fillMaxWidth())
    }
}
