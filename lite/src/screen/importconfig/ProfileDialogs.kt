package com.github.yumelira.yumebox.screen.importconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.component.DialogButtonRow
import com.github.yumelira.yumebox.core.model.Profile
import dev.oom_wg.purejoy.mlang.MLang
import java.io.File
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DeleteConfirmDialog(
    show: Boolean,
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
) {
    AppDialog(
        show = show,
        modifier = Modifier,
        title = MLang.ProfilesPage.DeleteDialog.Title,
        titleColor = DialogDefaults.titleColor(),
        summary = MLang.ProfilesPage.DeleteDialog.Message.format(profileName),
        summaryColor = DialogDefaults.summaryColor(),
        backgroundColor = DialogDefaults.backgroundColor(),
        enableWindowDim = true,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        outsideMargin = DialogDefaults.outsideMargin,
        insideMargin = DialogDefaults.insideMargin,
        defaultWindowInsetsPadding = true,
    ) {
        DialogButtonRow(
            onCancel = onDismiss,
            onConfirm = onConfirm,
            cancelText = MLang.ProfilesPage.Button.Cancel,
            confirmText = MLang.ProfilesPage.DeleteDialog.Confirm,
        )
    }
}

@Composable
internal fun ShareOptionsDialog(
    show: Boolean,
    profile: Profile,
    onDismiss: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    onShareFile: (Profile) -> Unit,
    onShareLink: (Profile) -> Unit,
) {
    AppDialog(
        show = show,
        modifier = Modifier,
        title = MLang.ProfilesPage.ShareDialog.Title,
        titleColor = DialogDefaults.titleColor(),
        backgroundColor = DialogDefaults.backgroundColor(),
        enableWindowDim = true,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        outsideMargin = DialogDefaults.outsideMargin,
        insideMargin = DialogDefaults.insideMargin,
        defaultWindowInsetsPadding = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (profile.type == Profile.Type.Url) {
                Button(
                    onClick = { onShareLink(profile) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        MLang.ProfilesPage.ShareDialog.ShareLink,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
            Button(
                onClick = { onShareFile(profile) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(MLang.ProfilesPage.ShareDialog.ShareFile)
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(MLang.ProfilesPage.Button.Cancel)
            }
        }
    }
}

internal fun importedConfigFile(filesDir: File, profile: Profile): File {
    return filesDir.resolve("imported").resolve(profile.uuid.toString()).resolve("config.yaml")
}
