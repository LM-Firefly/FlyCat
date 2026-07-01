package com.github.yumelira.yumebox.core.data

import com.github.yumelira.yumebox.core.model.AppIdentity
import com.github.yumelira.yumebox.core.model.AppRouteTrafficUsage
import com.github.yumelira.yumebox.core.model.AppTrafficUsage
import com.github.yumelira.yumebox.core.model.DailyAppTrafficSummary
import com.github.yumelira.yumebox.core.model.DailyTrafficSummary
import com.github.yumelira.yumebox.core.model.IpMonitoringState
import com.github.yumelira.yumebox.core.model.LinkOpenMode
import com.github.yumelira.yumebox.core.model.LogEntry
import com.github.yumelira.yumebox.core.model.LogFileInfo
import com.github.yumelira.yumebox.core.model.OverrideConfig
import com.github.yumelira.yumebox.core.model.ProfileBinding
import com.github.yumelira.yumebox.core.model.ProfileLink
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProxyDisplayMode
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.core.model.ProxySortMode
import com.github.yumelira.yumebox.core.model.RemoteBackend
import com.github.yumelira.yumebox.core.model.StatisticsTimeRange
import com.github.yumelira.yumebox.core.model.UpdateProvidersResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

data class Preference<T>(
    val state: StateFlow<T>,
    private val update: (T) -> Unit,
    private val get: () -> T,
    private val refreshState: () -> Unit = { update(get()) },
) {
    val value: T
        get() = get()

    fun set(value: T) = update(value)

    fun refresh() = refreshState()
}

fun <T> Preference<List<T>>.add(item: T) = set(value + item)

fun <T> Preference<List<T>>.remove(predicate: (T) -> Boolean) = set(value.filterNot(predicate))

fun <T> Preference<List<T>>.update(predicate: (T) -> Boolean, transform: (T) -> T) =
    set(value.map { if (predicate(it)) transform(it) else it })

/**
 * Read-only contract for network settings consumed by the runtime layer.
 * Decouples runtime:client from the concrete data:store implementation.
 */
interface NetworkSettingsReader {
    val proxyMode: Preference<ProxyMode>
}

/**
 * Read-only contract for app settings consumed by the runtime layer.
 */
interface AppSettingsReader {
    val automaticRestart: Preference<Boolean>
    val autoUpdateCurrentProfileOnStart: Preference<Boolean>
    val singleNodeTest: Preference<Boolean>
    val customUserAgent: Preference<String>
    val initialSetupCompleted: Preference<Boolean>
    val excludeFromRecents: Preference<Boolean>
}

/**
 * Read-only contract for feature store consumed by the runtime layer.
 */
interface FeatureStoreReader {
    fun consumePostUpdateColdStartPending(): Boolean
    val exitUiWhenBackground: Preference<Boolean>
}

/**
 * Read-only contract for proxy display settings consumed by feature modules.
 */
interface ProxyDisplaySettingsReader {
    val sortMode: Preference<ProxySortMode>
    val displayMode: Preference<ProxyDisplayMode>
    val sheetHeightFraction: Preference<Float>
}

/** Read/write contract for traffic statistics. */
interface TrafficStatisticsReader {
    val dailyAppSummaries: StateFlow<Map<Long, DailyAppTrafficSummary>>
    fun getAppUsagesSorted(range: StatisticsTimeRange): List<AppTrafficUsage>
    fun getTotalSummary(range: StatisticsTimeRange): DailyTrafficSummary
    fun clearAll()
}

/** Read/write contract for override config management. */
interface OverrideConfigRepository {
    suspend fun getUserConfigs(): List<OverrideConfig>
    suspend fun getById(id: String): OverrideConfig?
    fun getConfigContent(id: String): String?
    fun saveConfigContent(id: String, content: String): Boolean
    suspend fun save(config: OverrideConfig)
    suspend fun delete(id: String): Boolean
    suspend fun duplicate(id: String): OverrideConfig?
    suspend fun reorderUserConfigs(orderedIds: List<String>)
    suspend fun loadCustomRoutingContent(): String?
    suspend fun saveCustomRoutingContent(content: String)
}

/**
 * Read-only contract for remote controller store consumed by the runtime layer.
 * Decouples runtime:client from the concrete data:store implementation.
 */
interface RemoteControllerStoreReader {
    val controllerEnabled: Preference<Boolean>
    fun activeBackend(): RemoteBackend?
}

/** Contract for applying overrides to the active profile. */
interface OverrideApplier {
    suspend fun reapplyActiveProfileOverride(): Boolean
    suspend fun reapplyActiveProfileIfUsingOverride(overrideId: String): Boolean
    suspend fun isActiveProfileUsingOverride(overrideId: String): Boolean
}

/** Read-only contract for profile bindings. */
interface ProfileBindingReader {
    fun getAllBindingsFlow(): Flow<List<ProfileBinding>>
    suspend fun getBinding(profileId: String): ProfileBinding?
    suspend fun setBinding(binding: ProfileBinding)
    suspend fun isOverrideInUse(overrideId: String): Boolean
    suspend fun getOverrideUsageCount(overrideId: String): Int
}

/** Read/write contract for provider management. */
interface ProvidersRepository {
    suspend fun queryProviders(): Result<List<Provider>>
    suspend fun updateProvider(provider: Provider): Result<Unit>
    suspend fun updateAllProviders(providers: List<Provider>): Result<UpdateProvidersResult>
    suspend fun uploadProviderFile(
        context: Any,
        provider: Provider,
        uri: Any,
        maxBytes: Long = 5 * 1024 * 1024,
    ): Result<Unit>
}

/** Read/write contract for substore feature settings. */
interface SubStoreSettings {
    val allowLanAccess: Preference<Boolean>
    val backendPort: Preference<Int>
    val frontendPort: Preference<Int>
    val selectedPanelType: Preference<Int>
    val panelOpenMode: Preference<LinkOpenMode>
    val exitUiWhenBackground: Preference<Boolean>
    val subStoreAutoCloseModeOrdinal: Preference<Int>
}

/** Read-only contract for app update settings. */
interface UpdateSettings {
    val updateSourceKey: Preference<String>
    val autoCheckAppUpdate: Preference<Boolean>
}

/** Read-only contract for profile links settings. */
interface ProfileLinksReader {
    val linkOpenMode: Preference<LinkOpenMode>
    val links: Preference<List<ProfileLink>>
    val defaultLinkId: Preference<String>
}

/** Read-only contract for app identity resolution. */
interface AppIdentityReader {
    fun resolve(metadata: JsonObject): AppIdentity
    companion object {
        const val UNKNOWN_APP_NAME = "未知应用"
    }
}

/** Read-only contract for network info monitoring. */
interface NetworkInfoReader {
    fun triggerRefresh()
    fun startIpMonitoring(
        isProxyActiveFlow: kotlinx.coroutines.flow.Flow<Boolean>,
        externalRefreshFlow: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    ): kotlinx.coroutines.flow.Flow<IpMonitoringState>
}

/** Read/write contract for log management. */
interface LogStoreReader {
    val isRecordingState: kotlinx.coroutines.flow.StateFlow<Boolean>
    fun isRecording(): Boolean
    fun isCurrentRecordingFile(fileName: String): Boolean
    fun startRecording()
    fun stopRecording()
    fun listLogFiles(): List<LogFileInfo>
    suspend fun readLogEntries(fileName: String, maxEntries: Int = 2000): List<LogEntry>
    suspend fun exportLogFile(fileName: String, targetUri: Any): Boolean
    suspend fun exportMergedLog(fileName: String): String?
    suspend fun exportRecentLogsToUri(targetUri: Any): Boolean
    suspend fun readTempLogEntries(maxEntries: Int = 2000): List<LogEntry>
    suspend fun writeLogEntries(targetUri: Any, entries: List<LogEntry>): Boolean
    suspend fun deleteLogFile(fileName: String): Boolean
    suspend fun deleteAllLogs()
}

/** Read/write contract for app log buffer settings. */
interface AppLogSettings {
    var minLogLevel: Int
}
