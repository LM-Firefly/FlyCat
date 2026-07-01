package com.github.yumelira.yumebox.feature.proxy.presentation.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ProxyChainIndicator(
    chain: List<String>,
    modifier: Modifier = Modifier,
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
                Text(
                    text = nodeName,
                    modifier = Modifier.alignByBaseline(),
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
                        modifier = Modifier.alignByBaseline(),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
