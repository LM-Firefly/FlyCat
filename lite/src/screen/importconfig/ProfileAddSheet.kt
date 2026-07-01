package com.github.yumelira.yumebox.screen.importconfig

import android.content.ClipboardManager
import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.core.model.Profile
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import dev.oom_wg.purejoy.mlang.MLang
import java.io.File
import java.util.UUID
import kotlin.math.max
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AddProfileSheet(
    show: MutableState<Boolean>,
    profileToEdit: Profile? = null,
    importUrl: String? = null,
    initialType: Profile.Type? = null,
    onAddProfile:
        (
            name: String,
            source: String,
            type: Profile.Type,
            interval: Long,
            fileUri: android.net.Uri?,
            ageSecretKey: String,
        ) -> Unit,
    onUpdateProfile: (uuid: UUID, name: String, source: String, interval: Long) -> Unit,
    onDismissFinished: () -> Unit,
    onDownloadComplete: () -> Unit,
    viewModel: ImportConfigViewModel,
) {
    val configuration = LocalConfiguration.current
    val downloadSheetContentHeight = configuration.screenHeightDp.dp * 0.3f
    val downloadCompleteSheetContentHeight = configuration.screenHeightDp.dp * 0.42f
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedTypeIndex by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var ageSecretKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var hasShownCompleteAnimation by remember { mutableStateOf(false) }
    var stableSheetHeightPx by remember { mutableIntStateOf(0) }
    var launcherLaunchPending by remember { mutableStateOf(false) }

    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val urlPattern = remember { Regex("^https?://\\S+$", RegexOption.IGNORE_CASE) }

    val readClipboardAndCheckUrl: () -> String? = {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                clipText.takeIf { it.isNotBlank() && urlPattern.matches(it) }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(show.value, profileToEdit, importUrl, initialType) {
        if (show.value) {
            name = ""
            url = ""
            filePath = ""
            fileName = ""
            error = ""
            isDownloading = false
            hasShownCompleteAnimation = false

            when {
                profileToEdit != null -> {
                    name = profileToEdit.name
                    if (profileToEdit.type == Profile.Type.Url) {
                        selectedTypeIndex = 0
                        url = profileToEdit.source
                    } else {
                        selectedTypeIndex = 1
                        filePath = profileToEdit.source
                        fileName =
                            if (profileToEdit.source.isNotEmpty()) {
                                File(profileToEdit.source).name
                            } else {
                                ""
                            }
                    }
                }

                !importUrl.isNullOrBlank() -> {
                    selectedTypeIndex = 0
                    url = importUrl
                }

                initialType == Profile.Type.File -> {
                    selectedTypeIndex = 1
                }

                else -> {
                    selectedTypeIndex = 0
                    readClipboardAndCheckUrl()?.let { url = it }
                }
            }
        }
        onDispose {}
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error
        if (message != null) {
            context.toast(message)
            if (isDownloading) {
                isDownloading = false
                error = message
            }
        }
    }

    LaunchedEffect(downloadProgress?.isCompleted, isDownloading) {
        if (isDownloading && downloadProgress?.isCompleted == true && !hasShownCompleteAnimation) {
            hasShownCompleteAnimation = true
            onDownloadComplete()
        }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val actualFileName =
                    context.contentResolver
                        .query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            cursor.moveToFirst()
                            cursor.getString(nameIndex)
                        } ?: MLang.ProfilesPage.Message.UnknownFile

                val extension = actualFileName.substringAfterLast(".", "")
                if (
                    !extension.equals("yaml", ignoreCase = true) &&
                        !extension.equals("yml", ignoreCase = true)
                ) {
                    error = MLang.ProfilesPage.Validation.YamlOnly
                    return@let
                }

                filePath = it.toString()
                fileName = actualFileName
                error = ""

                val fileNameWithoutExt = actualFileName.substringBeforeLast(".")
                if (name.isBlank() || name == actualFileName) {
                    name = fileNameWithoutExt.ifBlank { MLang.ProfilesPage.Input.NewProfile }
                }
            }
        }

    LaunchedEffect(show.value, selectedTypeIndex) {
        if (show.value && selectedTypeIndex == 1 && profileToEdit == null && filePath.isBlank()) {
            launcherLaunchPending = true
        }
    }

    LaunchedEffect(launcherLaunchPending) {
        if (launcherLaunchPending) {
            launcherLaunchPending = false
            launcher.launch("*/*")
        }
    }

    val dismissSheet = {
        if (!isDownloading) {
            show.value = false
            viewModel.clearDownloadProgress()
        }
    }

    fun submitProfile() {
        if (isDownloading) return

        if (selectedTypeIndex == 0 && url.isBlank()) {
            error = MLang.ProfilesPage.Validation.EnterUrl
            return
        }
        if (selectedTypeIndex == 1 && filePath.isBlank()) {
            error = MLang.ProfilesPage.Validation.SelectFile
            return
        }

        keyboardController?.hide()
        viewModel.clearError()
        hasShownCompleteAnimation = false
        isDownloading = true

        if (selectedTypeIndex == 0) {
            if (profileToEdit != null) {
                onUpdateProfile(
                    profileToEdit.uuid,
                    name.ifBlank { profileToEdit.name },
                    url,
                    profileToEdit.interval,
                )
            } else {
                onAddProfile(
                    name.ifBlank { MLang.ProfilesPage.Input.NewProfile },
                    url,
                    Profile.Type.Url,
                    0L,
                    null,
                    ageSecretKey,
                )
            }
        } else {
            if (profileToEdit != null) {
                onUpdateProfile(
                    profileToEdit.uuid,
                    name.ifBlank { profileToEdit.name },
                    profileToEdit.source,
                    profileToEdit.interval,
                )
            } else {
                onAddProfile(
                    name.ifBlank { MLang.ProfilesPage.Input.NewProfile },
                    filePath,
                    Profile.Type.File,
                    0L,
                    filePath.toUri(),
                    ageSecretKey,
                )
            }
        }
    }

    AppActionBottomSheet(
        show = show.value,
        title =
            if (profileToEdit != null) MLang.ProfilesPage.Sheet.EditTitle
            else MLang.ProfilesPage.Sheet.AddTitle,
        startAction = {
            if (!isDownloading) {
                AppBottomSheetCloseAction(
                    contentDescription = MLang.Component.Button.Cancel,
                    onClick = dismissSheet,
                )
            }
        },
        endAction = {
            if (!isDownloading) {
                AppBottomSheetConfirmAction(
                    contentDescription = MLang.Component.Button.Confirm,
                    onClick = ::submitProfile,
                )
            }
        },
        onDismissRequest = dismissSheet,
        onDismissFinished = onDismissFinished,
    ) {
        val stableSheetHeight =
            remember(stableSheetHeightPx, density) {
                if (stableSheetHeightPx <= 0) 0.dp else with(density) { stableSheetHeightPx.toDp() }
            }

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .wrapContentHeight()
                    .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing))
                    .padding(bottom = 16.dp)
        ) {
            AnimatedContent(
                targetState = isDownloading,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(animationSpec = tween(260), initialOffsetX = { it }) +
                            fadeIn()) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(220),
                                targetOffsetX = { -it / 3 },
                            ) + fadeOut())
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(220),
                            initialOffsetX = { -it / 3 },
                        ) + fadeIn()) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(260),
                                targetOffsetX = { it },
                            ) + fadeOut())
                    }
                },
                label = "lite_profile_import_switch",
            ) { downloading ->
                if (downloading) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(
                                    if (downloadProgress?.isCompleted == true)
                                        downloadCompleteSheetContentHeight
                                    else if (stableSheetHeightPx > 0) stableSheetHeight
                                    else downloadSheetContentHeight
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement =
                                Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        ) {
                            InfiniteProgressIndicator(modifier = Modifier.size(32.dp))
                            Text(
                                text =
                                    downloadProgress?.message
                                        ?: MLang.ProfilesVM.Progress.Preparing,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth().onSizeChanged {
                                stableSheetHeightPx = max(stableSheetHeightPx, it.height)
                            },
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Card {
                            WindowSpinnerPreference(
                                title = MLang.ProfilesPage.Type.Title,
                                items =
                                    listOf(
                                        DropdownItem(MLang.ProfilesPage.Type.Subscription),
                                        DropdownItem(MLang.ProfilesPage.Type.LocalFile),
                                    ),
                                selectedIndex = selectedTypeIndex,
                                onSelectedIndexChange = {
                                    if (profileToEdit == null) {
                                        selectedTypeIndex = it
                                        error = ""
                                        if (it == 1 && filePath.isBlank()) {
                                            launcherLaunchPending = true
                                        }
                                    }
                                },
                            )
                        }

                        TextField(
                            value = name,
                            onValueChange = {
                                name = it
                                error = ""
                            },
                            label = MLang.ProfilesPage.Input.ProfileName,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (selectedTypeIndex == 0) {
                            TextField(
                                value = url,
                                onValueChange = {
                                    url = it
                                    error = ""
                                },
                                label = MLang.ProfilesPage.Input.SubscriptionUrl,
                                maxLines = 2,
                                readOnly = profileToEdit != null,
                                enabled = profileToEdit == null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            TextField(
                                value = fileName,
                                onValueChange = {},
                                label = MLang.ProfilesPage.Input.SelectFile,
                                readOnly = true,
                                modifier =
                                    Modifier.fillMaxWidth().clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) {
                                        launcher.launch("*/*")
                                    },
                            )
                        }

                        TextField(
                            value = ageSecretKey,
                            onValueChange = {
                                ageSecretKey = it
                            },
                            label = MLang.ProfilesPage.Input.AgeSecretKey,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (error.isNotEmpty()) {
                            Text(
                                text = error,
                                color = MiuixTheme.colorScheme.error,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }
        }
    }
}
