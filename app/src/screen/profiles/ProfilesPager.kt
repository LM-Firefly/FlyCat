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

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.App
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.core.model.ProfileBinding
import com.github.yumelira.yumebox.feature.override.presentation.util.OverrideEditorStore
import com.github.yumelira.yumebox.feature.override.presentation.viewmodel.OverrideConfigViewModel
import com.github.yumelira.yumebox.MainActivity
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.CenteredText
import com.github.yumelira.yumebox.presentation.component.LocalNavigator
import com.github.yumelira.yumebox.presentation.component.ProfileCard
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.icon.ShellIcons
import com.github.yumelira.yumebox.presentation.language.LanguageScope
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.presentation.theme.UiDp
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@SuppressLint("UseKtx")
@Composable
fun ProfilesPager(mainInnerPadding: PaddingValues) {
    val navigator = LocalNavigator.current
    val profilesViewModel = koinViewModel<ProfilesViewModel>()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val profiles by profilesViewModel.profiles.collectAsStateWithLifecycle()
    val isRunning by homeViewModel.isRunning.collectAsStateWithLifecycle()

    val overrideConfigViewModel = koinViewModel<OverrideConfigViewModel>()
    val userConfigs by overrideConfigViewModel.userConfigs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val showAddBottomSheet = remember { mutableStateOf(false) }
    var isDeleteDialogVisible by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Profile?>(null) }
    val showSettingsDialog = remember { mutableStateOf(false) }
    val showShareDialog = remember { mutableStateOf(false) }
    var isEditOptionsDialogVisible by remember { mutableStateOf(false) }
    var showEditOptionsDialog by remember { mutableStateOf<Profile?>(null) }
    var openConfigPreviewAfterEditDialogDismiss by remember { mutableStateOf(false) }
    var profileToShare by remember { mutableStateOf<Profile?>(null) }
    var profileToEdit by remember { mutableStateOf<Profile?>(null) }
    var profileBinding by remember { mutableStateOf<ProfileBinding?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    var importUrlFromScheme by remember { mutableStateOf<String?>(null) }
    val pendingImportUrl by MainActivity.pendingImportUrl.collectAsStateWithLifecycle()
    val urlProfiles = remember(profiles) { profiles.filter { it.type == Profile.Type.Url } }
    var scannedUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingImportUrl) {
        if (pendingImportUrl != null) {
            importUrlFromScheme = pendingImportUrl
            profileToEdit = null
            showAddBottomSheet.value = true
            MainActivity.clearPendingImportUrl()
        }
    }

    LaunchedEffect(showSettingsDialog.value) {
        if (showSettingsDialog.value) {
            overrideConfigViewModel.refresh()
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBar(
                title = MLang.ProfilesPage.Title,
                scrollBehavior = scrollBehavior,
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(UiDp.dp4)) {
                        IconButton(
                            onClick = {
                                if (!isDownloading) {
                                    isDownloading = true
                                    scope.launch {
                                        profilesViewModel.updateAllUrlProfiles()
                                        isDownloading = false
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = ShellIcons.UpdateProfiles,
                                contentDescription = MLang.ProfilesPage.Action.UpdateAll,
                            )
                        }
                        IconButton(
                            onClick = {
                                profileToEdit = null
                                showAddBottomSheet.value = true
                            },
                        ) {
                            Icon(
                                imageVector = ShellIcons.AddProfile,
                                contentDescription = MLang.ProfilesPage.Action.AddProfile,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            CenteredText(
                firstLine = MLang.ProfilesPage.Empty.NoProfiles,
                secondLine = MLang.ProfilesPage.Empty.Hint,
            )
        } else {
            val lazyListState = rememberLazyListState()
            val reorderableLazyListState =
                rememberReorderableLazyListState(lazyListState) { from, to ->
                    profilesViewModel.reorderProfiles(from.index, to.index)
                }

            ScreenLazyColumn(
                lazyListState = lazyListState,
                scrollBehavior = scrollBehavior,
                innerPadding = combinePaddingValues(innerPadding, mainInnerPadding),
                topPadding = UiDp.dp20,
            ) {
                items(items = profiles, key = { it.uuid.toString() }) { profile ->
                    ReorderableItem(reorderableLazyListState, key = profile.uuid.toString()) {
                        isDragging ->
                        ProfileCard(
                            profile = profile,
                            workDir = App.instance.filesDir.resolve("imported"),
                            isDownloading = isDownloading,
                            modifier =
                                Modifier.longPressDraggableHandle()
                                    .alpha(if (isDragging) 0.9f else 1f),
                            onExport = { profile ->
                                if (!isDownloading) {
                                    profileToShare = profile
                                    showShareDialog.value = true
                                }
                            },
                            onUpdate = { profile ->
                                if (!isDownloading) {
                                    isDownloading = true
                                    scope.launch {
                                        profilesViewModel.updateProfile(profile.uuid)
                                        isDownloading = false
                                    }
                                }
                            },
                            onDelete = { profile ->
                                if (!isDownloading) {
                                    showDeleteDialog = profile
                                    isDeleteDialogVisible = true
                                }
                            },
                            onEdit = { profile ->
                                if (!isDownloading) {
                                    showEditOptionsDialog = profile
                                    isEditOptionsDialogVisible = true
                                }
                            },
                            onToggleEnabled = { profile ->
                                if (!isDownloading) {
                                    scope.launch {
                                        if (profile.active && isRunning) {
                                            homeViewModel.stopProxy()
                                        }
                                        profilesViewModel.toggleProfileEnabled(profile.uuid)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    AddProfileSheet(
        show = showAddBottomSheet,
        profileToEdit = profileToEdit,
        importUrl = importUrlFromScheme ?: scannedUrl,
        onAddProfile = { name, source, type, interval, fileUri, ageSecretKey ->
            profilesViewModel.createProfile(type, name, source, interval, fileUri, ageSecretKey)
        },
        onUpdateProfile = { uuid, name, source, interval, ageSecretKey ->
            profilesViewModel.patchProfile(uuid, name, source, interval, ageSecretKey)
        },
        onDownloadComplete = {
            isDownloading = false
            showAddBottomSheet.value = false
            profilesViewModel.clearDownloadProgress()
        },
        profilesViewModel = profilesViewModel,
    )

    showDeleteDialog?.let { profile ->
        DeleteConfirmDialog(
            show = isDeleteDialogVisible,
            profileName = profile.name,
            onConfirm = {
                isDeleteDialogVisible = false
                profilesViewModel.deleteProfile(profile.uuid)
            },
            onDismiss = { isDeleteDialogVisible = false },
            onDismissFinished = { showDeleteDialog = null },
        )
    }

    val currentProfileToEdit = profileToEdit
    if (currentProfileToEdit != null) {
        ProfileSettingsDialog(
            show = showSettingsDialog.value,
            profile = currentProfileToEdit,
            userConfigs = userConfigs,
            binding = profileBinding,
            onDismiss = { showSettingsDialog.value = false },
            onDismissFinished = {
                profileToEdit = null
                profileBinding = null
            },
            onSaveProfileMeta = { newName, newSource, ageSecretKey ->
                if (newName.isNotBlank() && newSource.isNotBlank()) {
                    profilesViewModel.patchProfile(
                        currentProfileToEdit.uuid,
                        newName,
                        newSource,
                        currentProfileToEdit.interval,
                        ageSecretKey,
                    )
                }
            },
            onSaveOverrideSettings = { selectedOverrideIds ->
                scope.launch {
                    val profileId = currentProfileToEdit.uuid.toString()
                    profileBinding = profilesViewModel.saveOverrideBinding(
                        profileId = profileId,
                        overrideIds = selectedOverrideIds,
                        applyNow = isRunning && homeViewModel.isCurrentProfile(currentProfileToEdit.uuid),
                    )
                }
            },
        )
    }

    profileToShare?.let { profile ->
        ShareOptionsDialog(
            show = showShareDialog.value,
            profile = profile,
            onDismiss = { showShareDialog.value = false },
            onDismissFinished = { profileToShare = null },
            onShareFile = { profile ->
                val file = importedConfigFile(profile)

                if (!file.exists()) {
                    context.toast(
                        MLang.ProfilesPage.ShareDialog.ImportedConfigMissing.format(
                            file.absolutePath
                        )
                    )
                } else {
                    runCatching {
                            val uri =
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "application/x-yaml"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            context.startActivity(
                                Intent.createChooser(
                                        intent,
                                        MLang.ProfilesPage.ShareDialog.ShareFile,
                                    )
                                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            )
                        }
                        .onFailure { context.toast(it.message ?: "Share failed") }
                }
                showShareDialog.value = false
            },
            onShareLink = { profile ->
                val context = App.instance
                val url = if (profile.type == Profile.Type.Url) profile.source else null
                url?.let {
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, it)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(
                        Intent.createChooser(intent, MLang.ProfilesPage.ShareDialog.ShareLink)
                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                } ?: run { context.toast(MLang.ProfilesPage.ShareDialog.NoLink) }
                showShareDialog.value = false
            },
        )
    }

    showEditOptionsDialog?.let { profile ->
        ProfileEditOptionsDialog(
            show = isEditOptionsDialogVisible,
            onOpenConfig = {
                openConfigPreviewAfterEditDialogDismiss = false
                isEditOptionsDialogVisible = false
                openProfileConfigPreview(
                    targetFile = importedConfigFile(profile),
                    missingMessage =
                        MLang.ProfilesPage.SettingsDialog.ConfigMissing.format(
                            importedConfigFile(profile).absolutePath
                        ),
                    editable = true,
                    onReadFailed = context::toast,
                ) { configContent, callback ->
                    OverrideEditorStore.setupConfigPreview(
                        title = profile.name,
                        content = configContent,
                        language = LanguageScope.Yaml,
                        callback = callback,
                    )
                    openConfigPreviewAfterEditDialogDismiss = true
                }
            },
            onEditSettings = {
                openConfigPreviewAfterEditDialogDismiss = false
                isEditOptionsDialogVisible = false
                profileToEdit = profile
                scope.launch {
                    profileBinding = profilesViewModel.getBinding(profile.uuid.toString())
                }
                showSettingsDialog.value = true
            },
            onDismiss = {
                openConfigPreviewAfterEditDialogDismiss = false
                isEditOptionsDialogVisible = false
            },
            onDismissFinished = {
                showEditOptionsDialog = null
                if (openConfigPreviewAfterEditDialogDismiss) {
                    openConfigPreviewAfterEditDialogDismiss = false
                    navigator.push(Route.OverrideConfigPreview)
                }
            },
        )
    }
}
