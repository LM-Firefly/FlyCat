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

package com.github.lmfirefly.flycat.core.contract

import com.github.lmfirefly.flycat.core.model.AppIdentity
import com.github.lmfirefly.flycat.core.model.IpMonitoringState
import com.github.lmfirefly.flycat.core.model.LogEntry
import com.github.lmfirefly.flycat.core.model.LogFileInfo
import com.github.lmfirefly.flycat.core.model.Provider
import com.github.lmfirefly.flycat.core.model.UpdateProvidersResult
import com.github.lmfirefly.flycat.core.model.UpdateSource
import com.github.lmfirefly.flycat.core.model.profile.LinkOpenMode
import com.github.lmfirefly.flycat.core.model.traffic.AppTrafficUsage
import com.github.lmfirefly.flycat.core.model.traffic.DailyTraffic
import com.github.lmfirefly.flycat.core.model.traffic.StatisticsTimeRange
import com.github.lmfirefly.flycat.core.model.traffic.TimeSlotTraffic
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject

/** Read-only contract for app update settings. */
interface UpdateSettings {
    val updateSourceKey: Preference<String>
    val autoCheckAppUpdate: Preference<Boolean>
    fun getSelectedSource(): UpdateSource = UpdateSource.fromKey(updateSourceKey.value)
    fun setSelectedSource(source: UpdateSource) { updateSourceKey.set(source.key) }
}

/** Read-only contract for app identity resolution. */
interface AppIdentityReader {
    fun resolve(metadata: JsonObject): AppIdentity
    companion object { const val UNKNOWN_APP_NAME = "\u672a\u77e5\u5e94\u7528" }
}

/** Read-only contract for network info monitoring. */
interface NetworkInfoReader {
    fun triggerRefresh()
    fun startIpMonitoring(isProxyActiveFlow: Flow<Boolean>, externalRefreshFlow: Flow<Unit> = emptyFlow()): Flow<IpMonitoringState>
}

/** Read/write contract for app log buffer settings. */
interface AppLogSettings {
    var minLogLevel: Int
}

/** Read/write contract for traffic statistics. */
interface TrafficStatisticsRepository {
    fun getAppUsagesFlow(range: StatisticsTimeRange): Flow<List<AppTrafficUsage>>
    fun getTimeSlotTrafficFlow(range: StatisticsTimeRange): Flow<List<TimeSlotTraffic>>
    fun getDailyTrafficFlow(range: StatisticsTimeRange): Flow<List<DailyTraffic>>
    suspend fun getAppUsagesSorted(range: StatisticsTimeRange): List<AppTrafficUsage>
    fun clearAll()
}

/** Lifecycle contract for the traffic statistics collector. */
interface TrafficCollectorContract : java.io.Closeable {
    fun stop()
}

/** Read/write contract for provider management. */
interface ProvidersRepository {
    suspend fun queryProviders(): Result<List<Provider>>
    suspend fun updateProvider(provider: Provider): Result<Unit>
    suspend fun updateAllProviders(providers: List<Provider>): Result<UpdateProvidersResult>
    suspend fun uploadProviderFile(context: Any, provider: Provider, uri: Any, maxBytes: Long = 5 * 1024 * 1024): Result<Unit>
}

/** Read/write contract for Sub-Store feature settings. */
interface SubStoreSettings {
    val allowLanAccess: Preference<Boolean>
    val backendPort: Preference<Int>
    val frontendPort: Preference<Int>
    val selectedPanelType: Preference<Int>
    val panelOpenMode: Preference<LinkOpenMode>
    val exitUiWhenBackground: Preference<Boolean>
    val subStoreAutoCloseModeOrdinal: Preference<Int>
}

/** Read/write contract for log management. */
interface LogStoreReader {
    val isRecordingState: StateFlow<Boolean>
    val logDir: java.io.File
    fun isRecording(): Boolean
    fun isCurrentRecordingFile(fileName: String): Boolean
    fun startRecording()
    fun stopRecording()
    fun listLogFiles(): List<LogFileInfo>
    suspend fun readLogEntries(fileName: String, maxEntries: Int = 2000): List<LogEntry>
    suspend fun readLogEntriesSince(fileName: String, sinceByteOffset: Long, maxEntries: Int = 2000): Pair<List<LogEntry>, Long>
    suspend fun exportLogFile(fileName: String, targetUri: Any): Boolean
    suspend fun exportMergedLog(fileName: String): String?
    suspend fun exportRecentLogsToUri(targetUri: Any): Boolean
    fun setLogPreviewVisible(owner: String, visible: Boolean) {}
    suspend fun readTempLogEntries(maxEntries: Int = 2000): List<LogEntry>
    suspend fun writeLogEntries(targetUri: Any, entries: List<LogEntry>): Boolean
    suspend fun deleteLogFile(fileName: String): Boolean
    suspend fun deleteAllLogs()
}

/** Provides Sub-Store data paths and lifecycle control for backup/restore. */
interface SubStoreBackupSupport {
    val dataDir: File
    fun stopService(context: android.content.Context)
}

/** Provides navigation events from Sub-Store (e.g. open a URL). */
interface SubStoreNavigationHandler {
    val openUrlEvents: SharedFlow<String>
}

/** Contract for backup export/restore operations. */
interface BackupDataSource {
    suspend fun exportBackup(output: OutputStream)
    suspend fun restoreBackup(input: InputStream)
    fun defaultBackupFileName(now: Long = System.currentTimeMillis()): String
}
