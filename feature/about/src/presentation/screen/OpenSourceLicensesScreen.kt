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

package com.github.lmfirefly.flycat.feature.about.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.dialog.AppActionBottomSheet
import com.github.lmfirefly.flycat.presentation.component.layout.ScreenLazyColumn
import com.github.lmfirefly.flycat.presentation.component.layout.combinePaddingValues
import com.github.lmfirefly.flycat.presentation.component.layout.rememberStandalonePageMainPadding
import com.github.lmfirefly.flycat.presentation.component.navigation.NavigationBackIcon
import com.github.lmfirefly.flycat.presentation.component.navigation.TopBar
import com.github.lmfirefly.flycat.presentation.icon.FlyCat
import com.github.lmfirefly.flycat.presentation.icon.flycat.chevron
import com.github.lmfirefly.flycat.presentation.navigation.Navigator
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import com.github.lmfirefly.flycat.ui.platform.openUrl
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenSourceLicensesScreen(navigator: Navigator, librariesResId: Int) {
    val context = LocalContext.current
    val spacing = AppTheme.spacing
    val scrollBehavior = MiuixScrollBehavior()

    BackHandler { navigator.pop() }

    val libraries by produceLibraries(librariesResId)
    val libraryItems = remember(libraries) { libraries?.libraries.orEmpty().groupBy { it.name }.map { (name, group) -> LibraryGroup(name = name, entries = group.sortedBy { it.uniqueId }) } }

    Scaffold(
        topBar = { TopBar(title = FlyTxt.OpenSourceLicenses.Title, scrollBehavior = scrollBehavior, navigationIconPadding = 0.dp, navigationIcon = { NavigationBackIcon(navigator = navigator) }) }
    ) { innerPadding ->
        val mainLikePadding = rememberStandalonePageMainPadding()
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
        ) {
            if (libraryItems.isNotEmpty()) {
                items(
                    items = libraryItems,
                    key = { group -> "name:${group.name}" },
                ) { group ->
                    LibraryGroupItem(
                        group = group,
                        onOpenUrl = { url -> openUrl(context, url) },
                    )
                }
                item { Spacer(modifier = Modifier.height(spacing.space24)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryGroupItem(group: LibraryGroup, onOpenUrl: (String) -> Unit) {
    val spacing = AppTheme.spacing
    val library = group.representative
    val url = library.projectUrl
    val expandable = group.entries.size > 1
    var expanded by rememberSaveable(group.name) { mutableStateOf(false) }

    Card(
        modifier = Modifier.padding(bottom = spacing.space12),
        insideMargin = PaddingValues(spacing.space0),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            expandable -> Modifier.clickable { expanded = !expanded }
                            url != null -> Modifier.clickable { onOpenUrl(url) }
                            else -> Modifier
                        }
                    )
                    .padding(spacing.space16),
            verticalArrangement = Arrangement.spacedBy(spacing.space10),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.name,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                group.version?.let { version ->
                    Text(
                        text = version,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = spacing.space12),
                    )
                }
                if (expandable) {
                    Icon(
                        FlyCat.chevron,
                        contentDescription = null,
                        modifier = Modifier.padding(start = spacing.space8).size(18.dp).rotate(if (expanded) 90f else 0f),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            library.developers.firstOrNull()?.name?.let { author ->
                Text(
                    text = author,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            if (library.licenses.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.space6),
                    verticalArrangement = Arrangement.spacedBy(spacing.space6),
                ) {
                    library.licenses.forEach { license -> LicenseChip(licenseName = license.name) }
                }
            }

            if (expandable) {
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
                        group.entries.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = entry.uniqueId,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                entry.artifactVersion?.let { version ->
                                    Text(
                                        text = version,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(start = spacing.space12),
                                    )
                                }
                            }
                        }
                        if (url != null) {
                            Text(
                                text = url,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onOpenUrl(url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseChip(licenseName: String) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val opacity = AppTheme.opacity

    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(radii.radius12))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = opacity.subtle))
                .padding(horizontal = spacing.space10, vertical = spacing.space4)
    ) {
        Text(
            text = licenseName,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

private val Library.projectUrl: String?
    get() = website?.takeIf { it.isNotBlank() } ?: scm?.url?.takeIf { it.isNotBlank() }

 /** 同名多 artifact 的项目（如 Essenty 的 back-handler / lifecycle / state-keeper 等）在 AboutLibraries 中各自成条。按 name 归组：头部只展示一次，点击展开可查看组内全部 uniqueId。 */
private data class LibraryGroup(val name: String, val entries: List<Library>) {
    val representative: Library
        get() = entries.firstOrNull { !it.projectUrl.isNullOrBlank() } ?: entries.first()
    /** 组内版本一致时展示该版本；不一致则留空，展开行各自标注版本。 */
    val version: String?
        get() = entries.mapNotNull { it.artifactVersion }.distinct().singleOrNull()
}
