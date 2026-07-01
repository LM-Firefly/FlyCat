package com.github.yumelira.yumebox.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import java.net.URI

fun Context.openUrl(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun deriveProfileNameFromUrl(url: String): String {
    return runCatching { URI(url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() } }
        .getOrNull() ?: "新配置"
}

fun Context.resolveDisplayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                return cursor.getString(index)?.substringBeforeLast('.')?.ifBlank { "本地配置" }
                    ?: "本地配置"
            }
        }
    }

    return uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank {
        "本地配置"
    } ?: "本地配置"
}
