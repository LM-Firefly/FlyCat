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

package com.github.yumelira.yumebox.screen.profiles

import androidx.compose.foundation.layout.PaddingValues
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.github.yumelira.yumebox.presentation.util.getInfoText
import com.github.yumelira.yumebox.presentation.util.getDisplayProvider
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.github.yumelira.yumebox.App
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.icon.ShellIcons
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.runtime.api.Profile
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tf.gal.yumebox.locale.YumeTxt
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
internal fun ProfilesPageHost(
    mainInnerPadding: PaddingValues,
    profiles: List<Profile>,
    state: ProfilesDialogState,
    profilesViewModel: ProfilesViewModel,
    homeViewModel: HomeViewModel,
    isRunning: Boolean,
    windowLayoutMode: WindowLayoutMode = WindowLayoutMode.Compact,
    sheetHost: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var selectedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    // Profiles stay on the left in the dual-pane shell; no right-pane detail.
    val usesShellSplit = LocalDetailNavigator.current != null
    val usesMasterDetail = !usesShellSplit && windowLayoutMode.usesNavigationRail
    val showTwoPanes = !usesShellSplit && windowLayoutMode.usesTwoPanes

    LaunchedEffect(profiles, selectedProfileId, usesMasterDetail) {
        if (!usesMasterDetail) {
            selectedProfileId = null
            return@LaunchedEffect
        }
        if (profiles.isEmpty()) {
            selectedProfileId = null
            return@LaunchedEffect
        }
        val current = selectedProfileId
        if (current != null && profiles.any { it.uuid.toString() == current }) return@LaunchedEffect
        val active = profiles.firstOrNull { it.active }
        selectedProfileId = (active ?: profiles.first()).uuid.toString()
    }

    val selectedProfile =
        remember(profiles, selectedProfileId) {
            selectedProfileId?.let { id -> profiles.firstOrNull { it.uuid.toString() == id } }
        }
    val showDetail = showTwoPanes || (usesMasterDetail && selectedProfileId != null)

    BackHandler(enabled = usesMasterDetail && !showTwoPanes && selectedProfileId != null) {
        selectedProfileId = null
    }

    ProfilesPageContent(
        mainInnerPadding = mainInnerPadding,
        profiles = profiles,
        isDownloading = state.isDownloading,
        windowLayoutMode = windowLayoutMode,
        selectedProfile = selectedProfile,
        showDetail = showDetail,
        onSelectProfile = { profile -> selectedProfileId = profile.uuid.toString() },
        onClearDetail = { if (!showTwoPanes) selectedProfileId = null },
        onAddProfile = {
            state.profileToEdit = null
            state.showAdd.value = true
        },
        onReorderProfiles = { from, to -> profilesViewModel.reorderProfiles(from, to) },
        onShareProfile = { profile ->
            if (!state.isDownloading) {
                state.shareTarget = profile
                state.showShare.value = true
            }
        },
        onUpdateProfile = { profile ->
            if (!state.isDownloading) {
                state.isDownloading = true
                scope.launch {
                    profilesViewModel.updateProfile(profile.uuid)
                    state.isDownloading = false
                }
            }
        },
        onDeleteProfile = { profile ->
            if (!state.isDownloading) {
                state.deleteTarget = profile
                state.showDelete = true
            }
        },
        onEditProfile = { profile ->
            if (!state.isDownloading) {
                state.editOptionsTarget = profile
                state.showEditOptions = true
            }
        },
        onToggleProfile = { profile ->
            if (!state.isDownloading) {
                scope.launch {
                    if (profile.active && isRunning) {
                        homeViewModel.stopProxy()
                    }
                    profilesViewModel.toggleProfileEnabled(profile.uuid)
                }
            }
        },
        sheetHost = sheetHost,
    )
}

@Composable
internal fun ProfilesPageContent(
    mainInnerPadding: PaddingValues,
    profiles: List<Profile>,
    isDownloading: Boolean,
    windowLayoutMode: WindowLayoutMode = WindowLayoutMode.Compact,
    selectedProfile: Profile? = null,
    showDetail: Boolean = false,
    onSelectProfile: (Profile) -> Unit = {},
    onClearDetail: () -> Unit = {},
    onAddProfile: () -> Unit,
    onReorderProfiles: (Int, Int) -> Unit,
    onShareProfile: (Profile) -> Unit,
    onUpdateProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onToggleProfile: (Profile) -> Unit,
    sheetHost: @Composable () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val usesShellSplit = LocalDetailNavigator.current != null
    val usesMasterDetail = !usesShellSplit && windowLayoutMode.usesNavigationRail
    val showTwoPanes = !usesShellSplit && windowLayoutMode.usesTwoPanes

    Scaffold(
        topBar = {
            TopBar(
                title = YumeTxt.ProfilesPage.Title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (usesMasterDetail && !showTwoPanes && showDetail) {
                        IconButton(onClick = onClearDetail) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = YumeTxt.Component.Navigation.Back,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onAddProfile) {
                        Icon(
                            imageVector = ShellIcons.AddProfile,
                            contentDescription = YumeTxt.ProfilesPage.Action.AddProfile,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            if (profiles.isEmpty()) {
                CenteredText(
                    firstLine = YumeTxt.ProfilesPage.Empty.NoProfiles,
                    secondLine = YumeTxt.ProfilesPage.Empty.Hint,
                )
            } else if (usesMasterDetail) {
                MasterDetailLayout(
                    windowLayoutMode = windowLayoutMode,
                    showDetail = showDetail,
                    masterMinWidth = PaneWidths.ProfilesMasterMin,
                    masterMaxWidth = PaneWidths.ProfilesMasterMax,
                    master = {
                        ProfilesCompactList(
                            profiles = profiles,
                            mainInnerPadding = mainInnerPadding,
                            innerPadding = innerPadding,
                            scrollBehavior = scrollBehavior,
                            selectedProfileId = selectedProfile?.uuid?.toString(),
                            isDownloading = isDownloading,
                            onSelectProfile = onSelectProfile,
                        )
                    },
                    detail = {
                        ProfileDetailPane(
                            profile = selectedProfile,
                            mainInnerPadding = mainInnerPadding,
                            innerPadding = innerPadding,
                            isDownloading = isDownloading,
                            onShareProfile = onShareProfile,
                            onUpdateProfile = onUpdateProfile,
                            onDeleteProfile = onDeleteProfile,
                            onEditProfile = onEditProfile,
                            onToggleProfile = onToggleProfile,
                        )
                    },
                )
            } else {
                ProfilesList(
                    profiles = profiles,
                    mainInnerPadding = mainInnerPadding,
                    innerPadding = innerPadding,
                    scrollBehavior = scrollBehavior,
                    isDownloading = isDownloading,
                    onReorderProfiles = onReorderProfiles,
                    onShareProfile = onShareProfile,
                    onUpdateProfile = onUpdateProfile,
                    onDeleteProfile = onDeleteProfile,
                    onEditProfile = onEditProfile,
                    onToggleProfile = onToggleProfile,
                )
            }
            // Sheet composition is hosted here; dual-pane renders overlays in the left-pane root Scaffold.
            sheetHost()
        }
    }
}

@Composable
private fun ProfilesList(
    profiles: List<Profile>,
    mainInnerPadding: PaddingValues,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    isDownloading: Boolean,
    onReorderProfiles: (Int, Int) -> Unit,
    onShareProfile: (Profile) -> Unit,
    onUpdateProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onToggleProfile: (Profile) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            onReorderProfiles(from.index, to.index)
        }
    val importedDir = App.instance.filesDir.resolve("imported")

    ScreenLazyColumn(
        lazyListState = listState,
        scrollBehavior = scrollBehavior,
        innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
        topPadding = UiDp.dp20,
    ) {
        items(items = profiles, key = { it.uuid.toString() }) { profile ->
            ReorderableItem(reorderState, key = profile.uuid.toString()) { isDragging ->
                ProfileCard(
                    profile = profile,
                    workDir = importedDir,
                    isDownloading = isDownloading,
                    modifier =
                        Modifier.longPressDraggableHandle().alpha(if (isDragging) 0.9f else 1f),
                    onExport = { onShareProfile(it) },
                    onUpdate = { onUpdateProfile(it) },
                    onDelete = { onDeleteProfile(it) },
                    onEdit = { onEditProfile(it) },
                    onToggleEnabled = { onToggleProfile(it) },
                )
            }
        }
    }
}


@Composable
private fun ProfilesCompactList(
    profiles: List<Profile>,
    mainInnerPadding: PaddingValues,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    selectedProfileId: String?,
    isDownloading: Boolean,
    onSelectProfile: (Profile) -> Unit,
) {
    val opacity = AppTheme.opacity
    ScreenLazyColumn(
        scrollBehavior = scrollBehavior,
        innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
        topPadding = UiDp.dp20,
    ) {
        items(items = profiles, key = { it.uuid.toString() }) { profile ->
            val selected = profile.uuid.toString() == selectedProfileId
            com.github.yumelira.yumebox.presentation.component.Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = UiDp.dp12)
                        .background(
                            if (selected) {
                                MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtle)
                            } else {
                                MiuixTheme.colorScheme.surface.copy(alpha = 0f)
                            },
                            RoundedCornerShape(UiDp.dp16),
                        ),
                insideMargin = PaddingValues(UiDp.dp16),
                onClick = if (isDownloading) null else ({ onSelectProfile(profile) }),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight(550),
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = profile.getDisplayProvider(),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                    if (profile.active) {
                        Text(
                            text = "ON",
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDetailPane(
    profile: Profile?,
    mainInnerPadding: PaddingValues,
    innerPadding: PaddingValues,
    isDownloading: Boolean,
    onShareProfile: (Profile) -> Unit,
    onUpdateProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onToggleProfile: (Profile) -> Unit,
) {
    if (profile == null) {
        CenteredText(
            firstLine = YumeTxt.ProfilesPage.Empty.NoProfiles,
            secondLine = YumeTxt.ProfilesPage.Empty.Hint,
        )
        return
    }
    val workDir = App.instance.filesDir.resolve("imported")
    val scroll = rememberScrollState()
    val combined = combinePaddingValues(innerPadding, mainInnerPadding)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(combined)
                .padding(horizontal = UiDp.dp16, vertical = UiDp.dp20)
                .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp12),
    ) {
        ProfileCard(
            profile = profile,
            workDir = workDir,
            isDownloading = isDownloading,
            onExport = onShareProfile,
            onUpdate = onUpdateProfile,
            onDelete = onDeleteProfile,
            onEdit = onEditProfile,
            onToggleEnabled = onToggleProfile,
        )
    }
}
