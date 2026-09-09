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

package com.github.lmfirefly.flycat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.github.lmfirefly.flycat.core.model.override.OverrideConfig
import com.github.lmfirefly.flycat.core.model.override.OverrideContentType
import com.github.lmfirefly.flycat.feature.editor.presentation.screen.ConfigPreviewScreen
import com.github.lmfirefly.flycat.feature.override.presentation.screen.OverrideListScreen
import com.github.lmfirefly.flycat.feature.override.presentation.viewmodel.OverrideConfigViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.editor.LanguageScope
import com.github.lmfirefly.flycat.presentation.state.OverrideEditorStore
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun OverrideScreen(navigator: Navigator) {
    val overrideConfigViewModel: OverrideConfigViewModel = koinInject()
    val scope = rememberCoroutineScope()

    OverrideListScreen(
        onNavigateBack = { navigator.pop() },
        onOpenCodeEditor = { config: OverrideConfig ->
            scope.launch {
                val content = overrideConfigViewModel.getConfigContent(config.id) ?: config.content
                OverrideEditorStore.setupConfigPreview(
                    title = config.name,
                    content = content,
                    language = config.contentType.toLanguageScope(),
                    callback = { savedContent ->
                        if (!overrideConfigViewModel.saveConfigContent(config.id, savedContent)) {
                            error(FlyTxt.Override.Error.SaveFailed)
                        }
                    },
                )
                navigator.push(Route.OverrideConfigPreview)
            }
        }
    )
}

@Composable
fun OverrideConfigPreviewRoute(navigator: Navigator) {
    ConfigPreviewScreen(
        navigator = navigator,
        title = OverrideEditorStore.configPreviewTitle,
        initialContent = OverrideEditorStore.configPreviewContent,
        language = OverrideEditorStore.configPreviewLanguage,
        onSave = OverrideEditorStore.configPreviewCallback,
    )
}

private fun OverrideContentType.toLanguageScope(): LanguageScope =
    when (this) {
        OverrideContentType.Yaml -> LanguageScope.Yaml
        OverrideContentType.JavaScript -> LanguageScope.JavaScript
    }
