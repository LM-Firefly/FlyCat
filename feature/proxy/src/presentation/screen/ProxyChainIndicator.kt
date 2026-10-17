package com.github.lmfirefly.flycat.feature.proxy.presentation.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.lmfirefly.flycat.presentation.component.card.Card
import com.github.lmfirefly.flycat.presentation.component.misc.CountryFlag
import com.github.lmfirefly.flycat.presentation.component.misc.EmojiAwareText
import com.github.lmfirefly.flycat.presentation.component.misc.GroupIcon
import com.github.lmfirefly.flycat.presentation.component.misc.normalizeGroupIconUri
import com.github.lmfirefly.flycat.presentation.theme.UiDp
import com.github.lmfirefly.flycat.presentation.util.extractFlaggedName
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 代理链路指示器。链路段名前拼接策略组图标 / 节点国旗（可选），与主页策略组卡片的图标观感一致。 */
@Composable
fun ProxyChainIndicator(
    chain: List<String>,
    modifier: Modifier = Modifier,
    groupIconLookup: ((String) -> String?)? = null,
) {
    Card(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = UiDp.dp6, vertical = UiDp.dp6),
            horizontalArrangement = Arrangement.spacedBy(UiDp.dp4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chain.forEachIndexed { index, nodeName ->
                val iconUri = groupIconLookup?.invoke(nodeName)
                    ?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeGroupIconUri)
                // 名称自带 flag-emoji 时与图标重复：渲染图标（组图标或国旗）则用剥离后的名称
                val flaggedName = remember(nodeName) { extractFlaggedName(nodeName) }
                val showIcon = iconUri != null || flaggedName.countryCode != null
                if (iconUri != null) {
                    GroupIcon(iconUri = iconUri, width = UiDp.dp16)
                } else {
                    CountryFlag(countryCode = flaggedName.countryCode, width = UiDp.dp16)
                }
                EmojiAwareText(
                    text = if (showIcon) flaggedName.displayName else nodeName,
                    style = MiuixTheme.textStyles.body2,
                    color = if (index == chain.lastIndex) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    softWrap = false,
                )
                if (index < chain.lastIndex) {
                    Text(
                        text = "->",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
