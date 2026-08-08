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

package com.github.yumelira.yumebox.feature.profiles.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.feature.profiles.presentation.viewmodel.ProfilesViewModel
import com.github.yumelira.yumebox.presentation.component.CenteredText
import com.github.yumelira.yumebox.presentation.component.ProfileCard
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.BadgePlus
import com.github.yumelira.yumebox.presentation.icon.yume.CircleFadingArrowUp
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.runtime.api.contract.ProxyControlContract
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
internal fun ProfilesPageHost(
    mainInnerPadding: PaddingValues,
    profiles: List<Profile>,
    state: ProfilesDialogState,
    profilesViewModel: ProfilesViewModel,
    proxyFacade: ProxyControlContract,
    isRunning: Boolean,
    onUpdateAll: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

    ProfilesPageContent(
        mainInnerPadding = mainInnerPadding,
        profiles = profiles,
        isDownloading = state.isDownloading,
        onUpdateAll = onUpdateAll,
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
                        proxyFacade.stopProxy()
                    }
                    profilesViewModel.toggleProfileEnabled(profile.uuid)
                }
            }
        },
    )
}

@Composable
internal fun ProfilesPageContent(
    mainInnerPadding: PaddingValues,
    profiles: List<Profile>,
    isDownloading: Boolean,
    onUpdateAll: (() -> Unit)? = null,
    onAddProfile: () -> Unit,
    onReorderProfiles: (Int, Int) -> Unit,
    onShareProfile: (Profile) -> Unit,
    onUpdateProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onToggleProfile: (Profile) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.ProfilesPage.Title,
                scrollBehavior = scrollBehavior,
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp4)) {
                        if (onUpdateAll != null) {
                            IconButton(onClick = onUpdateAll) {
                                Icon(
                                    imageVector = Yume.CircleFadingArrowUp,
                                    contentDescription = FlyTxt.ProfilesPage.Action.UpdateAll,
                                )
                            }
                        }
                        IconButton(onClick = onAddProfile) {
                            Icon(
                                imageVector = Yume.BadgePlus,
                                contentDescription = FlyTxt.ProfilesPage.Action.AddProfile,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            CenteredText(
                firstLine = FlyTxt.ProfilesPage.Empty.NoProfiles,
                secondLine = FlyTxt.ProfilesPage.Empty.Hint,
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
    val importedDir = LocalContext.current.filesDir.resolve("imported")

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
