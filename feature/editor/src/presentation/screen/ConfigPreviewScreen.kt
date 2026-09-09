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

package com.github.lmfirefly.flycat.feature.editor.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.lmfirefly.flycat.feature.editor.presentation.editor.CodeEditor
import com.github.lmfirefly.flycat.feature.editor.presentation.editor.rememberConfiguredCodeEditorState
import com.github.lmfirefly.flycat.feature.editor.presentation.format.CodeFormatter
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.navigation.SmallTopBar
import com.github.lmfirefly.flycat.presentation.editor.LanguageScope
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.ArrowLeft
import com.github.lmfirefly.flycat.presentation.icon.flycat.ArrowRight
import com.github.lmfirefly.flycat.presentation.icon.flycat.ListCollapse
import com.github.lmfirefly.flycat.presentation.icon.flycat.Save
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.toast
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun ConfigPreviewScreen(
    navigator: Navigator,
    title: String = FlyTxt.Component.Editor.ConfigPreview.Title,
    initialContent: String = "",
    language: LanguageScope = LanguageScope.Yaml,
    readOnly: Boolean = false,
    onSave: (suspend (String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    val formattedContent =
        remember(initialContent, language) {
            if (language == LanguageScope.Json) {
                CodeFormatter.format(initialContent, language) ?: initialContent
            } else {
                initialContent
            }
        }

    val editorState =
        rememberConfiguredCodeEditorState(
            initialContent = formattedContent,
            language = language,
            readOnly = readOnly,
        )
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SmallTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = if (!readOnly) {{
                    Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp12)) {
                        IconButton(
                            onClick = { editorState.undo() },
                            enabled = editorState.canUndo(),
                        ) {
                            Icon(FlyCat.ArrowLeft, null)
                        }
                        IconButton(
                            onClick = { editorState.redo() },
                            enabled = editorState.canRedo(),
                        ) {
                            Icon(FlyCat.ArrowRight, null)
                        }
                    }
                }} else {{}},
                actions = if (!readOnly) {{
                    IconButton(
                        modifier = Modifier.padding(end = UiDp.dp12),
                        onClick = { editorState.format() },
                    ) {
                        Icon(FlyCat.ListCollapse, contentDescription = FlyTxt.Component.Editor.ConfigPreview.Format)
                    }
                    IconButton(
                        onClick = {
                            if (isSaving || onSave == null) return@IconButton
                            coroutineScope.launch {
                                isSaving = true
                                runCatching { onSave(editorState.content) }
                                    .onSuccess {
                                        editorState.resetModified()
                                        navigator.navigateUp()
                                    }
                                    .onFailure { context.toast(it.message ?: FlyTxt.Component.Editor.ConfigPreview.SaveFailed) }
                                isSaving = false
                            }
                        },
                        enabled = onSave != null && editorState.isModified && !isSaving,
                    ) {
                        Icon(FlyCat.Save, contentDescription = FlyTxt.Component.Editor.ConfigPreview.Save)
                    }
                }} else {{}},
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            CodeEditor(state = editorState, modifier = Modifier.fillMaxSize())
        }
    }
}
