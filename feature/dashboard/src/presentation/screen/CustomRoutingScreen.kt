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

package com.github.lmfirefly.flycat.feature.dashboard.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.OverridePresetItem
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.OverridePresetRegion
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.OverridePresetTemplateSelection
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.orderedBasePresetItems
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.orderedPresetRegions
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.orderedServicePresetItems
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.presetGroupTypeIconUrl
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.sortPresetItems
import com.github.lmfirefly.flycat.feature.dashboard.presentation.util.sortPresetRegions
import com.github.lmfirefly.flycat.feature.dashboard.presentation.viewmodel.CustomRoutingViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.RoutingSwitchCard
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.Edit
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.util.toast
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun CustomRoutingScreen(navigator: Navigator, onOpenYamlEditor: (title: String, content: String, onSave: suspend (String) -> Unit) -> Unit) {
    val viewModel: CustomRoutingViewModel = koinViewModel()
    val presetSelection by viewModel.presetSelection.collectAsStateWithLifecycle()
    val customRoutingContent by viewModel.customRoutingContent.collectAsStateWithLifecycle()
    val templateRoundTripSafe by viewModel.templateRoundTripSafe.collectAsStateWithLifecycle()
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

    fun applyPresetSelection(selection: OverridePresetTemplateSelection) {
        selectedUrlTestRegions.clear()
        selectedUrlTestRegions.addAll(sortPresetRegions(selection.urlTestRegions))
        selectedFallbackRegions.clear()
        selectedFallbackRegions.addAll(sortPresetRegions(selection.fallbackRegions))
        enabledItems.clear()
        enabledItems.addAll(sortPresetItems(selection.enabledItems))
        enableUrlTestGroup = selection.enableUrlTestGroup
        enableFallbackGroup = selection.enableFallbackGroup
        isDirty = false
    }

    fun editedPresetSelection() = OverridePresetTemplateSelection(
        urlTestRegions = selectedUrlTestRegions.toSet(),
        fallbackRegions = selectedFallbackRegions.toSet(),
        enabledItems = enabledItems.toSet(),
        enableUrlTestGroup = enableUrlTestGroup,
        enableFallbackGroup = enableFallbackGroup,
    )

    fun openEditor(content: String) {
        onOpenYamlEditor(FlyTxt.MetaFeature.CustomRouting.EditYaml, content) { edited ->
            viewModel.saveCustomRoutingYaml(edited).getOrElse { throw it }
        }
    }

    LaunchedEffect(presetSelection) { applyPresetSelection(presetSelection) }

    fun saveAndExit() {
        if (isSaving) return
        if (!isDirty) {
            navigator.navigateUp()
            return
        }
        // 手动 YAML 编辑优先于预设编辑——应丢弃后者，而不是将其覆盖。
        if (!templateRoundTripSafe) {
            applyPresetSelection(presetSelection)
            context.toast(FlyTxt.MetaFeature.CustomRouting.ManualYamlPresetDiscarded)
            navigator.navigateUp()
            return
        }

        scope.launch {
            isSaving = true
            viewModel
                .savePresetSelection(editedPresetSelection())
                .onSuccess {
                    isDirty = false
                    navigator.navigateUp()
                }
                .onFailure { error -> context.toast(error.message ?: FlyTxt.MetaFeature.CustomRouting.SaveFailed) }
            isSaving = false
        }
    }

    BackHandler { saveAndExit() }

    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.MetaFeature.CustomRouting.Title,
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 0.dp,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
                actions = {
                    IconButton(
                        enabled = !isSaving,
                        onClick = {
                            when {
                                !isDirty -> openEditor(customRoutingContent)
                                !templateRoundTripSafe -> {
                                    applyPresetSelection(presetSelection)
                                    context.toast(FlyTxt.MetaFeature.CustomRouting.ManualYamlPresetDiscarded)
                                    openEditor(customRoutingContent)
                                }
                                else -> {
                                    scope.launch {
                                        isSaving = true
                                        viewModel.savePresetSelection(editedPresetSelection()).onSuccess {
                                            isDirty = false
                                            openEditor(viewModel.customRoutingContent.value)
                                        }
                                        .onFailure { error ->
                                            context.toast(error.message ?: FlyTxt.MetaFeature.CustomRouting.SaveFailed)
                                        }
                                        isSaving = false
                                    }
                                }
                            }
                        },
                    ) {
                        Icon(imageVector = FlyCat.Edit, contentDescription = FlyTxt.MetaFeature.CustomRouting.EditButton)
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
                    title = FlyTxt.MetaFeature.CustomRouting.GroupTypeTitle,
                    items = listOf("urltest", "fallback"),
                    iconUrl = ::presetGroupTypeIconUrl,
                    itemTitle = { type ->
                        if (type == "urltest") {
                            FlyTxt.MetaFeature.CustomRouting.GroupTypeUrlTest
                        } else {
                            FlyTxt.MetaFeature.CustomRouting.GroupTypeFallback
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
                    title = FlyTxt.MetaFeature.CustomRouting.UrlTestRegionGroupTitle,
                    items = orderedPresetRegions(),
                    iconUrl = OverridePresetRegion::icon,
                    itemTitle = { it.localizedTitle() },
                    isChecked = { region -> region in selectedUrlTestRegions },
                    onCheckedChange = { region, checked ->
                        toggleSelection(selectedUrlTestRegions, region, checked)
                        isDirty = true
                    },
                )
            }

            item(key = "fallback-regions") {
                RoutingSwitchCard(
                    title = FlyTxt.MetaFeature.CustomRouting.FallbackRegionGroupTitle,
                    items = orderedPresetRegions(),
                    iconUrl = OverridePresetRegion::icon,
                    itemTitle = { it.localizedTitle() },
                    isChecked = { region -> region in selectedFallbackRegions },
                    onCheckedChange = { region, checked ->
                        toggleSelection(selectedFallbackRegions, region, checked)
                        isDirty = true
                    },
                )
            }

            item(key = "base-items") {
                RoutingSwitchCard(
                    title = FlyTxt.Override.Draft.BasicRouting,
                    items = orderedBasePresetItems(),
                    iconUrl = OverridePresetItem::icon,
                    itemTitle = { it.localizedTitle() },
                    isChecked = { item -> item in enabledItems },
                    onCheckedChange = { item, checked ->
                        toggleSelection(enabledItems, item, checked)
                        isDirty = true
                    },
                )
            }

            item(key = "service-items") {
                RoutingSwitchCard(
                    title = FlyTxt.Override.Draft.ServiceRouting,
                    items = orderedServicePresetItems(),
                    iconUrl = OverridePresetItem::icon,
                    itemTitle = { it.localizedTitle() },
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

private fun OverridePresetRegion.localizedTitle(): String = when (this) {
    OverridePresetRegion.HK -> FlyTxt.MetaFeature.CustomRouting.RegionHongKong
    OverridePresetRegion.TW -> FlyTxt.MetaFeature.CustomRouting.RegionTaiwan
    OverridePresetRegion.JP -> FlyTxt.MetaFeature.CustomRouting.RegionJapan
    OverridePresetRegion.SG -> FlyTxt.MetaFeature.CustomRouting.RegionSingapore
    OverridePresetRegion.US -> FlyTxt.MetaFeature.CustomRouting.RegionUnitedStates
    OverridePresetRegion.Other -> FlyTxt.MetaFeature.CustomRouting.RegionOther
}

private fun OverridePresetItem.localizedTitle(): String = when (this) {
    OverridePresetItem.Proxy -> FlyTxt.MetaFeature.CustomRouting.ItemProxy
    OverridePresetItem.Ads -> FlyTxt.MetaFeature.CustomRouting.ItemAds
    OverridePresetItem.Cn -> FlyTxt.MetaFeature.CustomRouting.ItemChina
    OverridePresetItem.GeolocationNotCn -> FlyTxt.MetaFeature.CustomRouting.ItemGlobal
    OverridePresetItem.Match -> FlyTxt.MetaFeature.CustomRouting.ItemMatch
    else -> title
}
