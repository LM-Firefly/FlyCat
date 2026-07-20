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

package com.github.yumelira.yumebox.feature.meta.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.feature.meta.presentation.util.*
import com.github.yumelira.yumebox.feature.meta.presentation.viewmodel.CustomRoutingViewModel
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.Edit
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun CustomRoutingScreen(
    onNavigateBack: () -> Unit,
    onOpenYamlEditor: (title: String, content: String, onSave: suspend (String) -> Unit) -> Unit,
) {
    val viewModel: CustomRoutingViewModel = koinViewModel()
    val presetSelection by viewModel.presetSelection.collectAsState()
    val customRoutingContent by viewModel.customRoutingContent.collectAsState()
    val templateRoundTripSafe by viewModel.templateRoundTripSafe.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedUrlTestRegions = remember { mutableStateListOf<OverridePresetRegion>() }
    val selectedFallbackRegions = remember { mutableStateListOf<OverridePresetRegion>() }
    val enabledItems = remember { mutableStateListOf<OverridePresetItem>() }
    var enableUrlTestGroup by remember { mutableStateOf(true) }
    var enableFallbackGroup by remember { mutableStateOf(false) }
    var isDirty by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(presetSelection) {
        selectedUrlTestRegions.clear()
        selectedUrlTestRegions.addAll(sortPresetRegions(presetSelection.urlTestRegions))
        selectedFallbackRegions.clear()
        selectedFallbackRegions.addAll(sortPresetRegions(presetSelection.fallbackRegions))
        enabledItems.clear()
        enabledItems.addAll(sortPresetItems(presetSelection.enabledItems))
        enableUrlTestGroup = presetSelection.enableUrlTestGroup
        enableFallbackGroup = presetSelection.enableFallbackGroup
        isDirty = false
    }

    fun saveAndExit() {
        if (isSaving) return
        if (!isDirty) {
            onNavigateBack()
            return
        }

        val updatedSelection =
            OverridePresetTemplateSelection(
                urlTestRegions = selectedUrlTestRegions.toSet(),
                fallbackRegions = selectedFallbackRegions.toSet(),
                enabledItems = enabledItems.toSet(),
                enableUrlTestGroup = enableUrlTestGroup,
                enableFallbackGroup = enableFallbackGroup,
            )
        scope.launch {
            isSaving = true
            viewModel
                .savePresetSelection(updatedSelection)
                .onSuccess {
                    isDirty = false
                    onNavigateBack()
                }
                .onFailure { error -> context.toast(error.message ?: "保存失败") }
            isSaving = false
        }
    }

    BackHandler { saveAndExit() }

    Scaffold(
        topBar = {
            TopBar(
                title = YumeTxt.MetaFeature.CustomRouting.Title,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        enabled = !isSaving,
                        onClick = {
                            onOpenYamlEditor(
                                YumeTxt.MetaFeature.CustomRouting.EditYaml,
                                customRoutingContent,
                            ) { content ->
                                viewModel.saveCustomRoutingYaml(content).getOrElse { throw it }
                            }
                        },
                    ) {
                        Icon(imageVector = Yume.Edit, contentDescription = "Edit")
                    }
                },
            )
        }
    ) { paddingValues ->
        val mainPadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(paddingValues, mainPadding),
        ) {
            item(key = "group-type") {
                RoutingSwitchCard(
                    title = YumeTxt.MetaFeature.CustomRouting.GroupTypeTitle,
                    items = listOf("urltest", "fallback"),
                    iconUrl = ::presetGroupTypeIconUrl,
                    itemTitle = { type ->
                        if (type == "urltest") {
                            YumeTxt.MetaFeature.CustomRouting.GroupTypeUrlTest
                        } else {
                            YumeTxt.MetaFeature.CustomRouting.GroupTypeFallback
                        }
                    },
                    isChecked = { type ->
                        if (type == "urltest") enableUrlTestGroup else enableFallbackGroup
                    },
                    onCheckedChange = { type, checked ->
                        if (type == "urltest") {
                            enableUrlTestGroup = checked
                        } else {
                            enableFallbackGroup = checked
                        }
                        isDirty = true
                    },
                )
            }

            item(key = "urltest-regions") {
                RoutingSwitchCard(
                    title = YumeTxt.MetaFeature.CustomRouting.UrlTestRegionGroupTitle,
                    items = orderedPresetRegions(),
                    iconUrl = OverridePresetRegion::icon,
                    itemTitle = OverridePresetRegion::displayName,
                    isChecked = { region -> region in selectedUrlTestRegions },
                    onCheckedChange = { region, checked ->
                        toggleSelection(selectedUrlTestRegions, region, checked)
                        isDirty = true
                    },
                )
            }

            item(key = "fallback-regions") {
                RoutingSwitchCard(
                    title = YumeTxt.MetaFeature.CustomRouting.FallbackRegionGroupTitle,
                    items = orderedPresetRegions(),
                    iconUrl = OverridePresetRegion::icon,
                    itemTitle = OverridePresetRegion::displayName,
                    isChecked = { region -> region in selectedFallbackRegions },
                    onCheckedChange = { region, checked ->
                        toggleSelection(selectedFallbackRegions, region, checked)
                        isDirty = true
                    },
                )
            }

            item(key = "base-items") {
                RoutingSwitchCard(
                    title = YumeTxt.Override.Draft.BasicRouting,
                    items = orderedBasePresetItems(),
                    iconUrl = OverridePresetItem::icon,
                    itemTitle = OverridePresetItem::title,
                    isChecked = { item -> item in enabledItems },
                    onCheckedChange = { item, checked ->
                        toggleSelection(enabledItems, item, checked)
                        isDirty = true
                    },
                )
            }

            item(key = "service-items") {
                RoutingSwitchCard(
                    title = YumeTxt.Override.Draft.ServiceRouting,
                    items = orderedServicePresetItems(),
                    iconUrl = OverridePresetItem::icon,
                    itemTitle = OverridePresetItem::title,
                    isChecked = { item -> item in enabledItems },
                    onCheckedChange = { item, checked ->
                        toggleSelection(enabledItems, item, checked)
                        isDirty = true
                    },
                )
            }
        }
    }
}

private fun <T> toggleSelection(items: MutableList<T>, item: T, checked: Boolean) {
    if (checked) {
        if (item !in items) {
            items.add(item)
        }
    } else {
        items.remove(item)
    }
}
