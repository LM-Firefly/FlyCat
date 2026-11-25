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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.lmfirefly.flycat.core.model.AgeKeyPair
import com.github.lmfirefly.flycat.core.util.crypto.AgeKeyCrypto
import com.github.lmfirefly.flycat.locale.FlyTxt
import com.github.lmfirefly.flycat.presentation.component.GlassTextButton
import com.github.lmfirefly.flycat.presentation.component.dialog.AppDialog
import com.github.lmfirefly.flycat.presentation.component.input.GlassTextField
import com.github.lmfirefly.flycat.presentation.theme.AppTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults

/**
 * Age 密钥生成器对话框。
 * secret/public 字段可编辑，以便输入或粘贴密钥，然后"Derive Public Key"从输入的 secret 推导公钥，或"Generate"通过原生 JNI 接口创建新的密钥对（x25519 或后量子混合）。
 * 生成/输入的密钥可端到端使用 —— Rust 覆盖解密器在运行时同时支持 x25519 和 mlkem768x25519 混合标识。
 */
@Composable
fun AgeKeyGeneratorDialog(show: Boolean, hybrid: Boolean, onDismiss: () -> Unit, onDismissFinished: () -> Unit) {
    val scope = rememberCoroutineScope()
    val spacing = AppTheme.spacing
    key(show, hybrid) {
        val secretKeyState = rememberTextFieldState()
        val publicKeyState = rememberTextFieldState()
        var generating by remember { mutableStateOf(false) }
        AppDialog(
            show = show,
            title = if (hybrid) FlyTxt.MetaFeature.AgeKey.HybridTitle else FlyTxt.MetaFeature.AgeKey.X25519Title,
            onDismissRequest = onDismiss,
            onDismissFinished = onDismissFinished,
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.space12)) {
                GlassTextField(
                    state = secretKeyState,
                    label = FlyTxt.MetaFeature.AgeKey.SecretKey,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassTextField(
                    state = publicKeyState,
                    label = FlyTxt.MetaFeature.AgeKey.PublicKey,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.space12)) {
                    GlassTextButton(
                        text = FlyTxt.MetaFeature.AgeKey.DerivePublicKey,
                        onClick = {
                            scope.launch {
                                val derived = AgeKeyCrypto.agePublicKey(secretKeyState.text.toString())
                                if (!derived.isNullOrBlank()) {
                                    publicKeyState.setTextAndPlaceCursorAtEnd(derived)
                                }
                            }
                        },
                        enabled = secretKeyState.text.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                    GlassTextButton(
                        text = FlyTxt.MetaFeature.AgeKey.Generate,
                        onClick = {
                            if (generating) return@GlassTextButton
                            generating = true
                            scope.launch {
                                val keyPair = if (hybrid) AgeKeyCrypto.genHybridKeyPair()
                                    else AgeKeyCrypto.genAgeKey()
                                generating = false
                                if (keyPair != null) {
                                    secretKeyState.setTextAndPlaceCursorAtEnd(keyPair.secretKey)
                                    publicKeyState.setTextAndPlaceCursorAtEnd(keyPair.publicKey)
                                }
                            }
                        },
                        enabled = !generating,
                        modifier = Modifier.weight(1f),
                        primary = true,
                    )
                }
            }
        }
    }
}
