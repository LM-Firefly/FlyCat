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

package com.github.yumelira.yumebox.feature.meta.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.contract.OverrideApplier
import com.github.yumelira.yumebox.core.contract.OverrideConfigRepository
import com.github.yumelira.yumebox.core.model.OverrideInternalConstants
import com.github.yumelira.yumebox.core.util.YamlCodec
import com.github.yumelira.yumebox.feature.meta.presentation.util.OverridePresetTemplateSelection
import com.github.yumelira.yumebox.feature.meta.presentation.util.analyzePresetTemplateContent
import com.github.yumelira.yumebox.feature.meta.presentation.util.buildPresetTemplateYaml
import com.github.yumelira.yumebox.feature.meta.presentation.util.defaultOverridePresetTemplateSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class CustomRoutingViewModel(
    private val overrideConfigRepository: OverrideConfigRepository,
    private val activeProfileOverrideApplier: OverrideApplier,
) : ViewModel() {
    private val presetSelectionState = MutableStateFlow(defaultOverridePresetTemplateSelection())
    val presetSelection: StateFlow<OverridePresetTemplateSelection> =
        presetSelectionState.asStateFlow()

    private val customRoutingContentState = MutableStateFlow("")
    val customRoutingContent: StateFlow<String> = customRoutingContentState.asStateFlow()

    private val templateRoundTripSafeState = MutableStateFlow(true)
    val templateRoundTripSafe: StateFlow<Boolean> = templateRoundTripSafeState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { reloadStateFromStoredContent() }
                .onFailure {
                    Timber.e(it, "Failed to reload custom routing state from stored content")
                }
        }
    }

    suspend fun savePresetSelection(
        updatedPresetSelection: OverridePresetTemplateSelection
    ): Result<Unit> = runCatching {
        val generatedYaml = buildPresetTemplateYaml(updatedPresetSelection)
        overrideConfigRepository.saveCustomRoutingContent(generatedYaml)
        applyContentState(generatedYaml)
        activeProfileOverrideApplier.reapplyActiveProfileIfUsingOverride(
            OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID
        )
    }

    suspend fun saveCustomRoutingYaml(content: String): Result<Unit> = runCatching {
        val contentToSave =
            if (content.isBlank()) {
                buildPresetTemplateYaml(defaultOverridePresetTemplateSelection())
            } else {
                YamlCodec.validate(content)
                content
            }
        overrideConfigRepository.saveCustomRoutingContent(contentToSave)
        applyContentState(contentToSave)
        activeProfileOverrideApplier.reapplyActiveProfileIfUsingOverride(
            OverrideInternalConstants.CUSTOM_ROUTING_OVERRIDE_ID
        )
    }

    private suspend fun reloadStateFromStoredContent() {
        applyContentState(overrideConfigRepository.loadCustomRoutingContent())
    }

    private fun applyContentState(content: String?) {
        val analysis = analyzePresetTemplateContent(content)
        customRoutingContentState.value = content.orEmpty()
        presetSelectionState.value = analysis.selection
        templateRoundTripSafeState.value = analysis.matchesTemplateExactly
    }
}
