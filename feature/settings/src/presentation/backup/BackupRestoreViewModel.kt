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

package com.github.yumelira.yumebox.feature.settings.presentation.backup

import android.app.Application
import android.net.Uri
import com.github.yumelira.yumebox.core.contract.BackupDataSource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tf.gal.yumebox.locale.FlyTxt

data class BackupRestoreUiState(
    val isBusy: Boolean = false,
)

sealed interface BackupRestoreEvent {
    data class Message(val text: String) : BackupRestoreEvent
    data object RestoreSuccess : BackupRestoreEvent
}

class BackupRestoreViewModel(
    private val application: Application,
    private val repository: BackupDataSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BackupRestoreEvent>()
    val events: SharedFlow<BackupRestoreEvent> = _events.asSharedFlow()

    fun defaultBackupFileName(): String = repository.defaultBackupFileName()

    fun exportBackup(uri: Uri) {
        launchBusy {
            application.contentResolver.openOutputStream(uri)?.use { output ->
                repository.exportBackup(output)
            } ?: error("Failed to open output stream")
        }
    }

    fun restoreBackup(uri: Uri) {
        launchBusy {
            application.contentResolver.openInputStream(uri)?.use { input ->
                repository.restoreBackup(input)
            } ?: error("Failed to open input stream")
            _events.emit(BackupRestoreEvent.RestoreSuccess)
        }
    }

    fun exportBackupToFile(file: java.io.File) {
        launchBusy {
            file.outputStream().use { output ->
                repository.exportBackup(output)
            }
        }
    }

    fun restoreBackupFromFile(file: java.io.File) {
        launchBusy {
            file.inputStream().use { input ->
                repository.restoreBackup(input)
            }
            _events.emit(BackupRestoreEvent.RestoreSuccess)
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onFailure { error ->
                    emitMessage(
                        FlyTxt.MetaFeature.Backup.RestoreFailed.format(
                            error.message ?: FlyTxt.Util.Error.UnknownError
                        )
                    )
                }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private suspend fun emitMessage(message: String) {
        _events.emit(BackupRestoreEvent.Message(message))
    }
}
