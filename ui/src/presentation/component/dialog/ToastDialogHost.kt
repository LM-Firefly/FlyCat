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

package com.github.lmfirefly.flycat.presentation.component.dialog

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassTextButton
import com.github.lmfirefly.flycat.presentation.util.ToastDialogBridge
import com.github.lmfirefly.flycat.presentation.util.ToastDialogEvent
import top.yukonga.miuix.kmp.layout.DialogDefaults

@Composable
fun ToastDialogHost() {
    val event by ToastDialogBridge.event.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var eventSnapshot by remember { mutableStateOf<ToastDialogEvent?>(null) }
    val showDialog = remember { mutableStateOf(false) }

    LaunchedEffect(event) {
        if (event != null) {
            eventSnapshot = event
            showDialog.value = true
        }
    }

    eventSnapshot?.let { snapshot ->
        AppDialog(
            show = showDialog.value,
            modifier = Modifier,
            title = snapshot.title,
            titleColor = DialogDefaults.titleColor(),
            summary = snapshot.message,
            summaryColor = DialogDefaults.summaryColor(),
            backgroundColor = DialogDefaults.backgroundColor(),
            enableWindowDim = true,
            onDismissRequest = { showDialog.value = false },
            onDismissFinished = {
                ToastDialogBridge.dismiss(snapshot.id)
                eventSnapshot = null
                showDialog.value = false
            },
            outsideMargin = DialogDefaults.outsideMargin,
            insideMargin = DialogDefaults.insideMargin,
            defaultWindowInsetsPadding = true,
            content = {
                GlassTextButton(
                    text = FlyTxt.Component.Button.Copy,
                    onClick = {
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val textToCopy = snapshot.message.ifBlank { snapshot.title }
                        clipboardManager.setPrimaryClip(ClipData.newPlainText(snapshot.title, textToCopy))
                        showDialog.value = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    primary = true,
                )
            },
        )
    }
}
