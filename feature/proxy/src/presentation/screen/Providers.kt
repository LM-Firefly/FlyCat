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

package com.github.lmfirefly.flycat.feature.proxy.presentation.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.core.model.Provider
import com.github.lmfirefly.flycat.core.model.SubscriptionInfo
import com.github.lmfirefly.flycat.core.util.format.formatBytes
import com.github.lmfirefly.flycat.core.util.path.runtimeHomeDir
import com.github.lmfirefly.flycat.feature.proxy.presentation.viewmodel.ProvidersViewModel
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.misc.CenteredText
import com.github.lmfirefly.flycat.presentation.component.misc.Title
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.CircleFadingArrowUp
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.navigation.Route
import com.github.lmfirefly.flycat.presentation.state.ProviderFilePreviewStore
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.toast
import java.io.File
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

private fun Provider.VehicleType.localizedDisplayName(): String =
    when (this) {
        Provider.VehicleType.HTTP -> FlyTxt.Providers.VehicleType.Http
        Provider.VehicleType.File -> FlyTxt.Providers.VehicleType.File
        Provider.VehicleType.Inline -> FlyTxt.Providers.VehicleType.Inline
        Provider.VehicleType.Compatible -> FlyTxt.Providers.VehicleType.Compatible
    }

private data class ProviderSection(
    val title: String,
    val providers: List<Provider>,
)

@Composable
fun ProvidersContent(navigator: Navigator) {
    val viewModel = koinViewModel<ProvidersViewModel>()
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current

    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(isRunning) {
        if (isRunning) {
            viewModel.refreshProviders()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            context.toast(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            context.toast(it, Toast.LENGTH_LONG)
            viewModel.clearError()
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val updatableProviders =
        remember(providers) { providers.filter { it.vehicleType == Provider.VehicleType.HTTP } }
    val sections =
        remember(providers) {
            val (proxyProviders, ruleProviders) =
                providers.partition { it.type == Provider.Type.Proxy }
            buildList {
                if (proxyProviders.isNotEmpty()) {
                    add(
                        ProviderSection(
                            title = FlyTxt.Providers.Type.ProxyProviders.format(proxyProviders.size),
                            providers = proxyProviders,
                        )
                    )
                }
                if (ruleProviders.isNotEmpty()) {
                    add(
                        ProviderSection(
                            title = FlyTxt.Providers.Type.RuleProviders.format(ruleProviders.size),
                            providers = ruleProviders,
                        )
                    )
                }
            }
        }

    Scaffold(
        topBar = {
            TopBar(
                title = FlyTxt.Providers.Title,
                scrollBehavior = scrollBehavior,
                navigationIconPadding = 0.dp,
                navigationIcon = { NavigationBackIcon(navigator = navigator) },
                actions = {
                    if (isRunning && updatableProviders.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateAllProviders() }) {
                            Icon(
                                imageVector = FlyCat.CircleFadingArrowUp,
                                contentDescription = FlyTxt.Providers.Action.UpdateAll,
                            )
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        if (!isRunning) {
            CenteredText(
                firstLine = FlyTxt.Providers.Empty.NotRunning,
                secondLine = FlyTxt.Providers.Empty.NotRunningHint,
                showEmptyResourceIllustration = true,
            )
        } else if (providers.isEmpty() && !uiState.isLoading) {
            CenteredText(
                firstLine = FlyTxt.Providers.Empty.NoProviders,
                secondLine = FlyTxt.Providers.Empty.NoProvidersHint,
                showEmptyResourceIllustration = true,
            )
        } else {
            val mainLikePadding = rememberStandalonePageMainPadding()
            ScreenLazyColumn(
                scrollBehavior = scrollBehavior,
                innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            ) {
                item(key = "providers_runtime_info") {
                    ProvidersInfoCard(basePath = context.runtimeHomeDir.absolutePath)
                }
                sections.forEach { section ->
                    providerSection(
                        section = section,
                        isUpdating = { providerKey ->
                            uiState.updatingProviders.contains(providerKey)
                        },
                        onUpdate = { provider -> viewModel.updateProvider(provider) },
                        onUpload = { provider, uri ->
                            viewModel.uploadProviderFile(context, provider, uri)
                        },
                        onView = { provider ->
                            coroutineScope.launch {
                                val file = File(provider.path)
                                if (file.exists()) {
                                    val content = withContext(Dispatchers.IO) {
                                        if (provider.format == Provider.Format.Mrs) {
                                            Clash.convertMrsToText(file.absolutePath)
                                        } else {
                                            file.readText()
                                        }
                                    }
                                    if (content != null) {
                                        ProviderFilePreviewStore.setup(
                                            title = provider.name,
                                            content = content,
                                        )
                                        navigator.push(Route.ProviderFilePreview)
                                    } else {
                                        context.toast(
                                            FlyTxt.Providers.Message.ConvertMrsFailed
                                        )
                                    }
                                } else {
                                    context.toast(
                                        FlyTxt.Providers.Message.OpenFileFailed.format(
                                            FlyTxt.Util.Error.UnknownError
                                        )
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    isUpdating: Boolean,
    onUpdate: () -> Unit,
    onUpload: (Uri) -> Unit,
    onView: () -> Unit,
) {
    val context = LocalContext.current
    val showPopup = remember { mutableStateOf(false) }
    val colorScheme = MiuixTheme.colorScheme
    val updateBg = remember(colorScheme) { colorScheme.primary.copy(alpha = 0.1f) }
    val updateTint = remember(colorScheme) { colorScheme.primary }

    val filePicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
            uri: Uri? ->
            uri?.let { onUpload(it) }
        }

    Card(modifier = Modifier.padding(vertical = UiDp.dp4)) {
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = UiDp.dp16, vertical = UiDp.dp12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    if (provider.count > 0) {
                        Spacer(modifier = Modifier.width(UiDp.dp8))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = UiDp.dp8, vertical = UiDp.dp2)
                        ) {
                            Text(
                                text = provider.count.toString(),
                                style = MiuixTheme.textStyles.footnote1,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (provider.path.isNotBlank()) {
                    Box {
                        IconButton(
                            backgroundColor = updateBg,
                            minHeight = UiDp.dp35,
                            minWidth = UiDp.dp35,
                            enabled = !isUpdating,
                            onClick = { showPopup.value = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = UiDp.dp10),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(UiDp.dp2),
                            ) {
                                Icon(
                                    modifier = Modifier.size(UiDp.dp20),
                                    imageVector = MiuixIcons.Edit,
                                    tint = updateTint,
                                    contentDescription = FlyTxt.Providers.Action.Operation,
                                )
                                Text(
                                    modifier = Modifier.padding(end = UiDp.dp3),
                                    text = FlyTxt.Providers.Action.Operation,
                                    color = updateTint,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                        }
                        val popupItems = remember { listOf(FlyTxt.Providers.Action.View, FlyTxt.Providers.Action.Update, FlyTxt.Providers.Action.Upload) }
                        WindowListPopup(
                            show = showPopup.value,
                            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                            alignment = PopupPositionProvider.Align.End,
                            onDismissRequest = { showPopup.value = false }
                        ) {
                            ListPopupColumn {
                                popupItems.forEachIndexed { index, item ->
                                    DropdownImpl(
                                        text = item,
                                        optionSize = popupItems.size,
                                        isSelected = false,
                                        onSelectedIndexChange = {
                                            showPopup.value = false
                                            when (index) {
                                                0 -> onView()
                                                1 -> onUpdate()
                                                2 -> filePicker.launch("*/*")
                                            }
                                        },
                                        index = index
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(UiDp.dp4))
            val percentageText = provider.subscriptionInfo?.let(::formatUsagePercent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(UiDp.dp8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = provider.vehicleType.localizedDisplayName(),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (provider.updatedAt > 0) {
                        Text(
                            text = "•",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = formatTimestamp(provider.updatedAt),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                if (percentageText != null) {
                    Text(
                        text = percentageText,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            provider.subscriptionInfo?.let { info ->
                val uTotal = info.total.toULong()
                if (uTotal > 0UL) {
                    val used = (info.upload.toULong() + info.download.toULong())
                    val total = uTotal
                    val remaining = if (total > used) total - used else 0uL
                    val fraction = (used.toDouble() / total.toDouble()).coerceIn(0.0, 1.2)
                    val percent = (fraction * 100.0).roundToInt()
                    val progressColor = when {
                        percent >= 90 -> MiuixTheme.colorScheme.error
                        percent >= 70 -> Color(0xFFFFB300)
                        else -> Color(0xFF4CAF50)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .background(progressColor.copy(alpha = 0.14f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .background(progressColor)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = FlyTxt.Providers.Info.UsedTraffic.format(
                                formatBytes(used),
                                formatBytes(total),
                            ),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = FlyTxt.Providers.Info.RemainingTraffic.format(formatBytes(remaining)),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                if (info.expire > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val diff = info.expire * 1000 - System.currentTimeMillis()
                    val days = diff / (1000 * 60 * 60 * 24)
                    val dateFormat = remember { java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd") }
                    val dateLabel = java.time.Instant.ofEpochMilli(info.expire * 1000).atZone(java.time.ZoneId.systemDefault()).format(dateFormat)
                    val suffix = when {
                        days >= 0 -> FlyTxt.Providers.Info.ExpireDays.format(dateLabel, days.toInt())
                        else -> FlyTxt.Providers.Info.Expired.format(dateLabel)
                    }
                    Text(
                        text = suffix,
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (diff > 0) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.error,
                    )
                }
            }
            if (provider.vehicleType == Provider.VehicleType.File && provider.path.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text(
                        text = FlyTxt.Providers.ProviderPath.format(provider.path),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProvidersInfoCard(basePath: String) {
    val context = LocalContext.current
    Card(modifier = Modifier.padding(vertical = 6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = FlyTxt.Providers.InfoTitle,
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = FlyTxt.Providers.InfoSummary.format(basePath),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable { openFlyCatDocumentsEntry(context) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                text = FlyTxt.Providers.Action.OpenInFiles,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

private fun openFlyCatDocumentsEntry(context: Context) {
    val authority = "${context.packageName}.files"
    val rootUri = DocumentsContract.buildRootUri(authority, "0")
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(rootUri, DocumentsContract.Root.MIME_TYPE_ITEM)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(viewIntent)
    }.recoverCatching {
        if (it is ActivityNotFoundException) {
            val fallbackIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } else {
            throw it
        }
    }.onFailure { error ->
        context.toast(
            FlyTxt.Providers.Message.OpenFileFailed.format(error.message ?: FlyTxt.Util.Error.UnknownError),
            Toast.LENGTH_LONG,
        )
    }
}

private fun LazyListScope.providerSection(
    section: ProviderSection,
    isUpdating: (String) -> Boolean,
    onUpdate: (Provider) -> Unit,
    onUpload: (Provider, Uri) -> Unit,
    onView: (Provider) -> Unit,
) {
    item(key = "title_${section.title}") { Title(section.title) }
    items(
        items = section.providers,
        key = { provider -> "${provider.type}_${provider.name}" },
        contentType = { "ProviderCard" },
    ) { provider ->
        val providerKey = "${provider.type}_${provider.name}"
        ProviderCard(
            provider = provider,
            isUpdating = isUpdating(providerKey),
            onUpdate = { onUpdate(provider) },
            onUpload = { uri -> onUpload(provider, uri) },
            onView = { onView(provider) },
        )
    }
}

private fun formatTimestamp(ts: Long): String =
    java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))

private fun formatUsagePercent(info: SubscriptionInfo): String? {
    val total = info.total.toULong()
    if (total == 0UL) return null
    val used = info.upload.toULong() + info.download.toULong()
    return String.format(Locale.getDefault(), "%.2f%%", used.toDouble() / total.toDouble() * 100.0)
}
