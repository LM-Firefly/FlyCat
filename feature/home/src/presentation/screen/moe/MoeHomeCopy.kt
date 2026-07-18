/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumelira.yumebox.feature.home.presentation.screen.moe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.util.Calendar
import tf.gal.yumebox.locale.FlyTxt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MoeHomeCopyBlock(
    nowMillis: Long,
    quoteText: String,
    quoteAuthor: String = "",
    color: Color,
    modifier: Modifier = Modifier,
    launchContent: @Composable (() -> Unit)? = null,
) {
    val greeting = remember(nowMillis) { moeGreetingText(nowMillis) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MoeUi.Quote.contentGap)) {
        Text(
            text = greeting,
            color = color.copy(alpha = MoeUi.Quote.eyebrowAlpha),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
            fontSize = MoeUi.Quote.eyebrowSize,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = quoteText,
            color = color.copy(alpha = 0.88f),
            modifier = Modifier.align(Alignment.End).padding(top = MoeUi.Quote.authorTopGap),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Medium,
            fontSize = MoeUi.Quote.textSize,
            lineHeight = MoeUi.Quote.lineHeight,
            softWrap = true,
            maxLines = 2,
            overflow = TextOverflow.Clip,
        )
        if (quoteAuthor.isNotBlank()) {
            Text(
                text = FlyTxt.Home.Quote.AuthorPrefix.format(quoteAuthor),
                color = color.copy(alpha = MoeUi.Quote.authorAlpha),
                modifier = Modifier.align(Alignment.End),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Normal,
                fontSize = MoeUi.Quote.authorSize,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        launchContent?.let { content ->
            Box(modifier = Modifier.fillMaxWidth().padding(top = MoeUi.Hero.launchTopGap)) { content() }
        }
    }
}

private fun moeGreetingText(nowMillis: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> FlyTxt.Home.Greeting.Morning
        in 12..17 -> FlyTxt.Home.Greeting.Afternoon
        in 18..23 -> FlyTxt.Home.Greeting.Evening
        else -> FlyTxt.Home.Greeting.Night
    }
}
