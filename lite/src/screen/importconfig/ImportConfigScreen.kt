package com.github.yumelira.yumebox.screen.importconfig

import android.content.Intent
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.MainActivity
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.*
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.`Badge-plus`
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun ImportConfigScreen(navigator: Navigator, prefillUrl: String = "") {
    val viewModel = koinViewModel<ImportConfigViewModel>()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val filesDir = remember(context) { context.filesDir }

    val showAddBottomSheet = remember { mutableStateOf(false) }
    var keepAddSheetComposed by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Profile?>(null) }
    var showShareDialog by remember { mutableStateOf<Profile?>(null) }
    var profileToEdit by remember { mutableStateOf<Profile?>(null) }
    var initialType by remember { mutableStateOf<Profile.Type?>(null) }
    var isDeleteDialogVisible by remember { mutableStateOf(false) }
    val showShareDialogVisible = remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var importUrlFromScheme by rememberSaveable { mutableStateOf<String?>(null) }

    val pendingImportUrl by MainActivity.pendingImportUrl.collectAsStateWithLifecycle()
    val urlProfiles = remember(profiles) { profiles.filter { it.type == Profile.Type.Url } }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(prefillUrl) {
        if (prefillUrl.isNotBlank()) {
            importUrlFromScheme = prefillUrl
            initialType = Profile.Type.Url
            profileToEdit = null
            keepAddSheetComposed = true
            showAddBottomSheet.value = true
        }
    }

    LaunchedEffect(pendingImportUrl) {
        if (!pendingImportUrl.isNullOrBlank()) {
            importUrlFromScheme = pendingImportUrl
            initialType = Profile.Type.Url
            profileToEdit = null
            keepAddSheetComposed = true
            showAddBottomSheet.value = true
            MainActivity.clearPendingImportUrl()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            viewModel.clearError()
            isDownloading = false
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            context.toast(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = MLang.ProfilesPage.Title,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        enabled = !isDownloading,
                        onClick = {
                            profileToEdit = null
                            importUrlFromScheme = null
                            initialType = null
                            keepAddSheetComposed = true
                            showAddBottomSheet.value = true
                        },
                    ) {
                        Icon(
                            imageVector = Yume.`Badge-plus`,
                            contentDescription = MLang.ProfilesPage.Action.AddProfile,
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            CenteredText(
                firstLine = MLang.ProfilesPage.Empty.NoProfiles,
                secondLine = MLang.ProfilesPage.Empty.Hint,
            )
        } else {
            val lazyListState = rememberLazyListState()
            ScreenLazyColumn(
                lazyListState = lazyListState,
                scrollBehavior = scrollBehavior,
                innerPadding =
                    combinePaddingValues(innerPadding, rememberStandalonePageMainPadding()),
                topPadding = 20.dp,
            ) {
                items(items = profiles, key = { it.uuid.toString() }) { profile ->
                    ProfileCard(
                        profile = profile,
                        workDir = filesDir.resolve("imported"),
                        isDownloading = isDownloading,
                        modifier = Modifier.alpha(1f),
                        onUpdate = {
                            if (!isDownloading) {
                                isDownloading = true
                                viewModel.updateProfile(profile.uuid) { isDownloading = false }
                            }
                        },
                        onDelete = {
                            if (!isDownloading) {
                                showDeleteDialog = profile
                                isDeleteDialogVisible = true
                            }
                        },
                        onEdit = {
                            if (!isDownloading) {
                                profileToEdit = profile
                                importUrlFromScheme = null
                                initialType = profile.type
                                keepAddSheetComposed = true
                                showAddBottomSheet.value = true
                            }
                        },
                        onExport = {
                            if (!isDownloading) {
                                showShareDialog = profile
                                showShareDialogVisible.value = true
                            }
                        },
                        onToggleEnabled = {
                            if (!isDownloading) {
                                viewModel.toggleProfileEnabled(
                                    profile.uuid,
                                    stopService = profile.active && isRunning,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (keepAddSheetComposed) {
        AddProfileSheet(
            show = showAddBottomSheet,
            profileToEdit = profileToEdit,
            importUrl = importUrlFromScheme,
            initialType = initialType,
            onAddProfile = { name, source, type, interval, fileUri, ageSecretKey ->
                isDownloading = true
                viewModel.createProfile(type, name, source, interval, fileUri, ageSecretKey) {
                    isDownloading = false
                }
            },
            onUpdateProfile = { uuid, name, source, interval ->
                isDownloading = true
                viewModel.patchProfile(uuid, name, source, interval) { isDownloading = false }
            },
            onDismissFinished = {
                keepAddSheetComposed = false
                importUrlFromScheme = null
                profileToEdit = null
                initialType = null
            },
            onDownloadComplete = {
                isDownloading = false
                showAddBottomSheet.value = false
                importUrlFromScheme = null
                profileToEdit = null
                initialType = null
                viewModel.clearDownloadProgress()
            },
            viewModel = viewModel,
        )
    }

    showDeleteDialog?.let { profile ->
        DeleteConfirmDialog(
            show = isDeleteDialogVisible,
            profileName = profile.name,
            onConfirm = {
                isDeleteDialogVisible = false
                viewModel.deleteProfile(profile.uuid)
            },
            onDismiss = { isDeleteDialogVisible = false },
            onDismissFinished = { showDeleteDialog = null },
        )
    }

    showShareDialog?.let { profile ->
        ShareOptionsDialog(
            show = showShareDialogVisible.value,
            profile = profile,
            onDismiss = { showShareDialogVisible.value = false },
            onDismissFinished = { showShareDialog = null },
            onShareFile = { target ->
                val file = importedConfigFile(filesDir, target)
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
                showShareDialogVisible.value = false
            },
            onShareLink = { target ->
                val url = if (target.type == Profile.Type.Url) target.source else null
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
                } ?: context.toast(MLang.ProfilesPage.ShareDialog.NoLink)
                showShareDialogVisible.value = false
            },
        )
    }
}
