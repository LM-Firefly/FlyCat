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

package com.github.lmfirefly.flycat.feature.settings.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.model.NotificationLineContent
import com.github.lmfirefly.flycat.core.model.NotificationLineValues
import com.github.lmfirefly.flycat.core.model.resolve
import com.github.lmfirefly.flycat.feature.settings.presentation.viewmodel.AppSettingsViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassScaffold
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.dialog.AppTextFieldDialog
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceArrowItem
import com.github.lmfirefly.flycat.presentation.component.misc.PreferenceEnumItem
import com.github.lmfirefly.flycat.presentation.component.misc.Title
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.component.rememberAppIconBitmap
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 通知内容设置二级页：预览卡片 + 两行内容来源选择。
 * 预览与真实通知共用 core 的 [com.github.lmfirefly.flycat.core.model.resolve] 解析器，保证所见即所得。
 */
@Composable
fun NotificationSettingsScreen(navigator: Navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val viewModel = koinViewModel<AppSettingsViewModel>()
    val firstLine by viewModel.notificationFirstLine.state.collectAsStateWithLifecycle()
    val secondLine by viewModel.notificationSecondLine.state.collectAsStateWithLifecycle()
    val firstLineText by viewModel.notificationFirstLineText.state.collectAsStateWithLifecycle()
    val secondLineText by viewModel.notificationSecondLineText.state.collectAsStateWithLifecycle()

    GlassScaffold(
        topBar = {
            TopBar(
                title = FlyTxt.AppSettings.Notification.ContentSection,
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 0.dp,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
            )
        },
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            item {
                Title(FlyTxt.AppSettings.Notification.PreviewSection)
                Card {
                    NotificationPreviewCard(
                        firstLine = firstLine,
                        secondLine = secondLine,
                        firstLineText = firstLineText,
                        secondLineText = secondLineText,
                    )
                }
            }
            item {
                Title(FlyTxt.AppSettings.Notification.ContentSection)
                Card {
                    PreferenceEnumItem(
                        title = FlyTxt.AppSettings.Notification.FirstLineTitle,
                        currentValue = firstLine,
                        items = NotificationLineContent.entries.filter { it != NotificationLineContent.Hidden }.map { notificationLineLabel(it) },
                        values = NotificationLineContent.entries.filter { it != NotificationLineContent.Hidden },
                        onValueChange = viewModel::onNotificationFirstLineChange,
                    )
                    if (firstLine == NotificationLineContent.CustomText) {
                        NotificationCustomTextRow(
                            text = firstLineText,
                            onTextChange = viewModel::onNotificationFirstLineTextChange,
                        )
                    }
                    PreferenceEnumItem(
                        title = FlyTxt.AppSettings.Notification.SecondLineTitle,
                        currentValue = secondLine,
                        items = NotificationLineContent.entries.map { notificationLineLabel(it) },
                        values = NotificationLineContent.entries,
                        onValueChange = viewModel::onNotificationSecondLineChange,
                    )
                    if (secondLine == NotificationLineContent.CustomText) {
                        NotificationCustomTextRow(
                            text = secondLineText,
                            onTextChange = viewModel::onNotificationSecondLineTextChange,
                        )
                    }
                }
            }
        }
    }
}

/** 预览样本取值（仅示意；真正的一致性由共用 resolve() 保证，见 [NotificationPreviewCard]）。 */
private val PreviewValues = NotificationLineValues(
    profileName = "FlyCat",
    currentNode = "香港-Media",
    liveTraffic = "↓ 1.2 MB/s ↑ 120 KB/s",
    usedTraffic = "12.7 GB",
    sessionTraffic = "2.6 GB",
    totalTraffic = "193.2 GB",
    remainingTraffic = "180.5 GB",
    subscriptionUsage = "6.6%",
    expiryDate = "2027-01-01",
    status = FlyTxt.AppSettings.Notification.LineStatus,
)

/** 仿真实通知布局的预览卡片：图标 + 标题行（内容+时间戳）+ 第二行内容。 */
@Composable
private fun NotificationPreviewCard(
    firstLine: NotificationLineContent,
    secondLine: NotificationLineContent,
    firstLineText: String,
    secondLineText: String,
) {
    val context = LocalContext.current
    val appIcon = rememberAppIconBitmap(context.packageName, 96)
    val title = firstLine.resolve(PreviewValues, firstLineText).ifBlank { PreviewValues.profileName }
    val content = secondLine.resolve(PreviewValues, secondLineText)
    val timeText = remember(context) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = UiDp.dp16, vertical = UiDp.dp12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = null,
                modifier = Modifier.size(UiDp.dp40).clip(RoundedCornerShape(UiDp.dp10)),
            )
            Spacer(modifier = Modifier.width(UiDp.dp12))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(UiDp.dp8))
                Text(
                    text = timeText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (content.isNotBlank()) {
                Text(
                    text = content,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun notificationLineLabel(content: NotificationLineContent): String = when (content) {
    NotificationLineContent.ProfileName -> FlyTxt.AppSettings.Notification.LineProfileName
    NotificationLineContent.Status -> FlyTxt.AppSettings.Notification.LineStatus
    NotificationLineContent.CurrentNode -> FlyTxt.AppSettings.Notification.LineCurrentNode
    NotificationLineContent.LiveTraffic -> FlyTxt.AppSettings.Notification.LineLiveTraffic
    NotificationLineContent.UsedTraffic -> FlyTxt.AppSettings.Notification.LineUsedTraffic
    NotificationLineContent.SessionTraffic -> FlyTxt.AppSettings.Notification.LineSessionTraffic
    NotificationLineContent.TotalTraffic -> FlyTxt.AppSettings.Notification.LineTotalTraffic
    NotificationLineContent.RemainingTraffic -> FlyTxt.AppSettings.Notification.LineRemainingTraffic
    NotificationLineContent.SubscriptionUsage -> FlyTxt.AppSettings.Notification.LineSubscriptionUsage
    NotificationLineContent.ExpiryDate -> FlyTxt.AppSettings.Notification.LineExpiryDate
    NotificationLineContent.CustomText -> FlyTxt.AppSettings.Notification.LineCustomText
    NotificationLineContent.Hidden -> FlyTxt.AppSettings.Notification.LineHidden
}

@Composable
private fun NotificationCustomTextRow(text: String, onTextChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    PreferenceArrowItem(
        title = FlyTxt.AppSettings.Notification.CustomTextTitle,
        summary = text.ifBlank { FlyTxt.AppSettings.Notification.CustomTextEmpty },
        onClick = { editing = true },
    )
    AppTextFieldDialog(
        show = editing,
        title = FlyTxt.AppSettings.Notification.CustomTextTitle,
        value = text,
        onValueChange = onTextChange,
        onDismissRequest = { editing = false },
        onConfirm = { editing = false },
        singleLine = true,
    )
}
