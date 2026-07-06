/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
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
 *
 */

package com.github.yumelira.yumebox.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.model.AgeKeyPair
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

/**
 * Age key generator dialog. The secret/public fields are editable so a key can be typed or pasted,
 * then "Derive Public Key" derives the public key from the entered secret, or "Generate" creates a
 * fresh key pair (x25519 or post-quantum hybrid) through the native JNI surface. Generated/entered
 * keys are usable end to end — the Rust override decryptor supports both x25519 and mlkem768x25519
 * hybrid identities at runtime.
 */
@Composable
fun AgeKeyGeneratorDialog(
    show: Boolean,
    hybrid: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val spacing = AppTheme.spacing

    var secretKey by remember(show, hybrid) { mutableStateOf("") }
    var publicKey by remember(show, hybrid) { mutableStateOf("") }
    var generating by remember(show, hybrid) { mutableStateOf(false) }

    AppDialog(
        show = show,
        title =
            if (hybrid) MLang.MetaFeature.AgeKey.HybridTitle else MLang.MetaFeature.AgeKey.X25519Title,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            TextField(
                value = secretKey,
                onValueChange = { secretKey = it },
                label = MLang.MetaFeature.AgeKey.SecretKey,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = publicKey,
                onValueChange = { publicKey = it },
                label = MLang.MetaFeature.AgeKey.PublicKey,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                TextButton(
                    text = MLang.MetaFeature.AgeKey.DerivePublicKey,
                    onClick = {
                        scope.launch {
                            val derived =
                                withContext(Dispatchers.Default) {
                                    Clash.toPublicKeys(listOf(secretKey))?.firstOrNull()
                                }
                            if (!derived.isNullOrBlank()) {
                                publicKey = derived
                            }
                        }
                    },
                    enabled = secretKey.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = MLang.MetaFeature.AgeKey.Generate,
                    onClick = {
                        if (generating) return@TextButton
                        generating = true
                        scope.launch {
                            val keyPair =
                                withContext(Dispatchers.Default) {
                                    if (hybrid) Clash.genHybridKeyPair()
                                    else Clash.genX25519KeyPair()?.let { AgeKeyPair(it.first, it.second) }
                                }
                            generating = false
                            if (keyPair != null) {
                                secretKey = keyPair.secretKey
                                publicKey = keyPair.publicKey
                            }
                        }
                    },
                    enabled = !generating,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
