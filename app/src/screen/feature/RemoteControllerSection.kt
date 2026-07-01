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

package com.github.yumelira.yumebox.screen.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.github.yumelira.yumebox.core.model.RemoteBackend
import com.github.yumelira.yumebox.platform.util.toast
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.PreferenceArrowItem
import com.github.yumelira.yumebox.presentation.component.PreferenceSwitchItem
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.github.yumelira.yumebox.screen.settings.RemoteControllerViewModel
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RemoteControllerSection(
    viewModel: RemoteControllerViewModel = koinViewModel(),
) {
    val context = LocalContext.current

    val controllerEnabled by viewModel.controllerEnabled.collectAsState()
    val backends by viewModel.backends.collectAsState()
    val activeBackendId by viewModel.activeBackendId.collectAsState()

    var sheetState by remember { mutableStateOf<BackendSheetState?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { message -> context.toast(message) } }

    val activeBackend = backends.firstOrNull { it.id == activeBackendId } ?: backends.firstOrNull()
    val selectedBackendIndex =
        backends.indexOfFirst { it.id == activeBackend?.id }.takeIf { it >= 0 } ?: 0
    val backendItems = backends.map { it.displayName() }

    Title(MLang.Feature.RemoteController.Section)
    Card {
        PreferenceSwitchItem(
            title = MLang.Feature.RemoteController.ModeTitle,
            checked = controllerEnabled,
            onCheckedChange = viewModel::setEnabled,
            enabled = backends.isNotEmpty(),
        )

        if (backends.isNotEmpty()) {
            WindowDropdownPreference(
                title = MLang.Feature.RemoteController.ControlBackend,
                summary = null,
                items = backendItems,
                selectedIndex = selectedBackendIndex,
                onSelectedIndexChange = { index ->
                    backends.getOrNull(index)?.let { viewModel.setActive(it.id) }
                },
            )
        }

        PreferenceArrowItem(
            title = MLang.Feature.RemoteController.AddBackend,
            onClick = {
                sheetState = BackendSheetState.Add(BackendFormState.empty())
                sheetVisible = true
            },
        )

        activeBackend?.let { backend ->
            PreferenceArrowItem(
                title = MLang.Feature.RemoteController.EditBackend,
                onClick = {
                    sheetState = BackendSheetState.Edit(BackendFormState.from(backend))
                    sheetVisible = true
                },
            )
        }
    }

    sheetState?.let { state ->
        BackendEditSheet(
            show = sheetVisible,
            state = state.form,
            title = if (state is BackendSheetState.Add) MLang.Feature.RemoteController.AddBackend else MLang.Feature.RemoteController.EditBackend,
            onDismiss = { sheetVisible = false },
            onDismissFinished = { sheetState = null },
            onConfirm = { name, host, port, secret ->
                when (state) {
                    is BackendSheetState.Add -> viewModel.addBackend(name, host, port, secret)
                    is BackendSheetState.Edit ->
                        state.form.id?.let { id ->
                            viewModel.updateBackend(
                                RemoteBackend(
                                    id = id,
                                    name = name.trim(),
                                    host = host.trim(),
                                    port = port,
                                    secret = secret.trim(),
                                )
                            )
                        }
                }
                sheetVisible = false
            },
            onDelete =
                (state as? BackendSheetState.Edit)?.let { editState ->
                    {
                        editState.form.id?.let(viewModel::deleteBackend)
                        sheetVisible = false
                    }
                },
        )
    }
}

@Composable
private fun BackendEditSheet(
    show: Boolean,
    state: BackendFormState,
    title: String,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: (name: String, host: String, port: Int, secret: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val spacing = AppTheme.spacing
    var name by remember(state) { mutableStateOf(state.name) }
    var host by remember(state) { mutableStateOf(state.host) }
    var port by remember(state) { mutableStateOf(state.port) }
    var secret by remember(state) { mutableStateOf(state.secret) }

    val normalizedHost = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    val parsedPort = port.trim().toIntOrNull()
    val portValid = parsedPort != null && parsedPort in 1..65535
    val canConfirm = normalizedHost.isNotEmpty() && portValid

    AppDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.space16),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = MLang.Feature.RemoteController.Name,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                    verticalAlignment = Alignment.Top,
                ) {
                    TextField(
                        value = host,
                        onValueChange = { raw ->
                            val input = parseHostPortInput(raw, port)
                            host = input.host
                            port = input.port
                        },
                        label = MLang.Feature.RemoteController.Host,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = MLang.Feature.RemoteController.Port,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.45f),
                    )
                }

                TextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = MLang.Feature.RemoteController.Secret,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!portValid && port.isNotBlank()) {
                    Text(
                        text = MLang.Feature.RemoteController.PortRangeError,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(start = spacing.space12),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space16),
            ) {
                if (onDelete != null) {
                    TextButton(
                        text = MLang.Feature.RemoteController.Delete,
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.textButtonColors(
                                textColor = MiuixTheme.colorScheme.error
                            ),
                    )
                } else {
                    TextButton(
                        text = MLang.Component.Button.Cancel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    text = MLang.Component.Button.Confirm,
                    onClick = { if (canConfirm) onConfirm(name, normalizedHost, parsedPort, secret) },
                    modifier = Modifier.weight(1f),
                    enabled = canConfirm,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

private sealed interface BackendSheetState {
    val form: BackendFormState

    data class Add(override val form: BackendFormState) : BackendSheetState

    data class Edit(override val form: BackendFormState) : BackendSheetState
}

private data class BackendFormState(
    val id: String?,
    val name: String,
    val host: String,
    val port: String,
    val secret: String,
) {
    companion object {
        fun empty() = BackendFormState(id = null, name = "", host = "", port = "9090", secret = "")

        fun from(backend: RemoteBackend) =
            BackendFormState(
                id = backend.id,
                name = backend.name,
                host = backend.host,
                port = backend.port.toString(),
                secret = backend.secret,
            )
    }
}

private fun RemoteBackend.displayName(): String = name.ifBlank { "${host}:${port}" }

private data class HostPortInput(val host: String, val port: String)

private fun parseHostPortInput(raw: String, currentPort: String): HostPortInput {
    val endpoint =
        raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore('?')
            .trimEnd('/')
    val lastColonIndex = endpoint.lastIndexOf(':')
    if (lastColonIndex > 0 && endpoint.indexOf(':') == lastColonIndex) {
        val possiblePort = endpoint.substring(lastColonIndex + 1)
        if (possiblePort.isNotBlank() && possiblePort.all(Char::isDigit)) {
            return HostPortInput(
                host = endpoint.substring(0, lastColonIndex),
                port = possiblePort.take(5),
            )
        }
    }
    return HostPortInput(host = endpoint, port = currentPort)
}
