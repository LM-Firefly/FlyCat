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

package com.github.yumelira.yumebox.screen.navigation

import androidx.compose.runtime.Composable
import com.github.yumelira.yumebox.core.model.OverrideConfig
import com.github.yumelira.yumebox.core.model.OverrideContentType
import com.github.yumelira.yumebox.feature.editor.presentation.screen.ConfigPreviewScreen
import com.github.yumelira.yumebox.feature.override.presentation.screen.OverrideListScreen
import com.github.yumelira.yumebox.feature.override.presentation.util.OverrideEditorStore
import com.github.yumelira.yumebox.feature.override.presentation.viewmodel.OverrideConfigViewModel
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.language.LanguageScope
import com.github.yumelira.yumebox.presentation.navigation.Route
import org.koin.compose.koinInject

@Composable
fun OverrideScreen(navigator: Navigator) {
    val overrideConfigViewModel: OverrideConfigViewModel = koinInject()

    OverrideListScreen(
        onNavigateBack = { navigator.pop() },
        onOpenCodeEditor = { config: OverrideConfig ->
            OverrideEditorStore.setupConfigPreview(
                title = config.name,
                content = overrideConfigViewModel.getConfigContent(config.id) ?: config.content,
                language = config.contentType.toLanguageScope(),
                callback = { content ->
                    if (!overrideConfigViewModel.saveConfigContent(config.id, content)) {
                        error("保存覆写失败")
                    }
                },
            )
            navigator.push(Route.OverrideConfigPreview)
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
