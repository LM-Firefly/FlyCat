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

package com.github.yumelira.yumebox.feature.editor.presentation.diagnostic

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import tf.gal.yumebox.locale.FlyTxt
import timber.log.Timber

object JsonDiagnosticsProvider {
    @Suppress("TooGenericExceptionCaught")
    fun analyze(content: String): DiagnosticsContainer {
        val container = DiagnosticsContainer()

        if (content.isBlank()) {
            return container
        }

        val trimmed = content.trim()

        try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> {
                    container.addDiagnostic(
                        DiagnosticRegion(
                            0,
                            content.length.coerceAtMost(1),
                            DiagnosticRegion.SEVERITY_ERROR,
                            0,
                            DiagnosticDetail(
                                briefMessage = FlyTxt.Editor.JsonDiagnostic.FormatError,
                                detailedMessage = FlyTxt.Editor.JsonDiagnostic.MustStartWithBraceOrBracket,
                            ),
                        )
                    )
                }
            }
        } catch (error: JSONException) {
            val diagnostic = parseJsonException(error, content)
            if (diagnostic != null) {
                container.addDiagnostic(diagnostic)
            }
        } catch (error: Exception) { // fault barrier: diagnostics must never crash the editor
            Timber.w(error, "JSON analysis failed")
        }

        return container
    }

    private fun parseJsonException(error: JSONException, content: String): DiagnosticRegion? {
        val message = error.message ?: return null

        val indexPattern = "character (\\d+)".toRegex()
        val match = indexPattern.find(message)

        val errorIndex = match?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val safeIndex = errorIndex.coerceIn(0, content.length - 1)
        val endIndex = (safeIndex + 1).coerceAtMost(content.length)

        return DiagnosticRegion(
            safeIndex,
            endIndex,
            DiagnosticRegion.SEVERITY_ERROR,
            0,
            DiagnosticDetail(
                briefMessage = FlyTxt.Editor.JsonDiagnostic.SyntaxError,
                detailedMessage = formatErrorMessage(message),
            ),
        )
    }

    private fun formatErrorMessage(message: String): String =
        when {
            message.contains("Unterminated") -> FlyTxt.Editor.JsonDiagnostic.UnterminatedStringOrObject
            message.contains("Expected") -> {
                val expectedPattern = "Expected (\\S+)".toRegex()
                val expected = expectedPattern.find(message)?.groupValues?.get(1) ?: FlyTxt.Editor.JsonDiagnostic.Unknown
                FlyTxt.Editor.JsonDiagnostic.Expected.format(expected)
            }
            message.contains("No value") -> FlyTxt.Editor.JsonDiagnostic.MissingValue
            message.contains("Duplicate") -> FlyTxt.Editor.JsonDiagnostic.DuplicateKey
            else -> message
        }
}
