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

package com.github.lmfirefly.flycat.feature.meta.presentation.screen

import androidx.compose.runtime.Composable
import com.github.lmfirefly.flycat.presentation.editor.LanguageScope
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.navigation.Route
import com.github.lmfirefly.flycat.presentation.state.OverrideEditorStore

@Composable
fun CustomRoutingRoute(navigator: Navigator) {
    CustomRoutingScreen(
        navigator = navigator,
        onOpenYamlEditor = { title, content, onSave ->
            OverrideEditorStore.setupConfigPreview(
                title = title,
                content = content,
                language = LanguageScope.Yaml,
                callback = onSave,
            )
            navigator.push(Route.OverrideConfigPreview)
        },
    )
}
