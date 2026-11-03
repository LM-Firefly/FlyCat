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

@file:UseSerializers(DateSerializer::class)

package com.github.lmfirefly.flycat.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.lmfirefly.flycat.core.util.serialization.DateSerializer
import com.github.lmfirefly.flycat.core.util.serialization.Parcelizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.Date

@Serializable
data class LogMessage(
    val level: Level,
    val message: String,
    val time: Date,
) : Parcelable {
    @Serializable
    enum class Level {
        @SerialName("debug") Debug,
        @SerialName("info") Info,
        @SerialName("warning") Warning,
        @SerialName("error") Error,
        @SerialName("silent") Silent,
        @SerialName("unknown") Unknown,
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<LogMessage> {
            override fun createFromParcel(parcel: Parcel): LogMessage = Parcelizer.decodeFromParcel(serializer(), parcel)
            override fun newArray(size: Int): Array<LogMessage?> = arrayOfNulls(size)
        }
    }
}

/**
 * 日志消息的分隔符编解码器：`level \u0001 timeMillis \u0001 message`，与原生 logcat 负载同构。
 * 用于本地解析与 Root 链路缓冲/传输，避免每行一次 kotlinx JSON 的 encode/decode。
 * message 置于末尾，按前两个分隔符切分，故 message 内含 \u0001 也可无损还原。
 */
object LogMessageCodec {
    private val fallbackJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun encodeDelimited(message: LogMessage): String = buildString {
        append(levelName(message.level))
        append('\u0001')
        append(message.time.time)
        append('\u0001')
        append(message.message)
    }

    fun decodeDelimited(payload: String): LogMessage {
        val sep1 = payload.indexOf('\u0001')
        if (sep1 > 0) {
            val sep2 = payload.indexOf('\u0001', sep1 + 1)
            if (sep2 > sep1) {
                val time = payload.substring(sep1 + 1, sep2).toLongOrNull()
                if (time != null) {
                    return LogMessage(
                        level = levelOf(payload.substring(0, sep1)),
                        message = payload.substring(sep2 + 1),
                        time = Date(time),
                    )
                }
            }
        }
        return fallbackJson.decodeFromString(LogMessage.serializer(), payload)
    }

    private fun levelOf(name: String): LogMessage.Level = when (name) {
        "debug" -> LogMessage.Level.Debug
        "info" -> LogMessage.Level.Info
        "warning" -> LogMessage.Level.Warning
        "error" -> LogMessage.Level.Error
        "silent" -> LogMessage.Level.Silent
        else -> LogMessage.Level.Unknown
    }

    private fun levelName(level: LogMessage.Level): String = when (level) {
        LogMessage.Level.Debug -> "debug"
        LogMessage.Level.Info -> "info"
        LogMessage.Level.Warning -> "warning"
        LogMessage.Level.Error -> "error"
        LogMessage.Level.Silent -> "silent"
        LogMessage.Level.Unknown -> "unknown"
    }
}

data class LogFileInfo(
    val name: String,
    val createdAt: Long,
    val size: Long,
    val isRecording: Boolean,
)

data class LogEntry(
    val time: String,
    val level: LogMessage.Level,
    val message: String,
)
