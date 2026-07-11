package com.github.yumelira.yumebox.core.data

import com.github.yumelira.yumebox.core.model.AccessControlMode
import com.github.yumelira.yumebox.core.model.AppColorTheme
import com.github.yumelira.yumebox.core.model.AppIdentity
import com.github.yumelira.yumebox.core.model.AppLanguage
import com.github.yumelira.yumebox.core.model.AppTrafficUsage
import com.github.yumelira.yumebox.core.model.ConnectionSnapshot
import com.github.yumelira.yumebox.core.model.Imported
import com.github.yumelira.yumebox.core.model.IpMonitoringState
import com.github.yumelira.yumebox.core.model.LinkOpenMode
import com.github.yumelira.yumebox.core.model.LogEntry
import com.github.yumelira.yumebox.core.model.LogFileInfo
import com.github.yumelira.yumebox.core.model.OverrideConfig
import com.github.yumelira.yumebox.core.model.ProfileBinding
import com.github.yumelira.yumebox.core.model.Provider
import com.github.yumelira.yumebox.core.model.ProxyDisplayMode
import com.github.yumelira.yumebox.core.model.ProxyGroupInfo
import com.github.yumelira.yumebox.core.model.ProxyMode
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.ProxySortMode
import com.github.yumelira.yumebox.core.model.RemoteBackend
import com.github.yumelira.yumebox.core.model.RootTunDnsMode
import com.github.yumelira.yumebox.core.model.StatisticsTimeRange
import com.github.yumelira.yumebox.core.model.ThemeMode
import com.github.yumelira.yumebox.core.model.TunStack
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
 * Contract for network settings consumed by runtime and feature modules.
 */
interface NetworkSettingsReader {
    val proxyMode: Preference<ProxyMode>
    val bypassPrivateNetwork: Preference<Boolean>
    val dnsHijack: Preference<Boolean>
    val allowBypass: Preference<Boolean>
    val enableIPv6: Preference<Boolean>
    val systemProxy: Preference<Boolean>
    val tunStack: Preference<TunStack>
    val tunRouteExcludeAddress: Preference<List<String>>
    val rootTunIfName: Preference<String>
    val rootTunMtu: Preference<Int>
    val rootTunAutoRoute: Preference<Boolean>
    val rootTunStrictRoute: Preference<Boolean>
    val rootTunAutoRedirect: Preference<Boolean>
    val rootTunIncludeAndroidUser: Preference<List<Int>>
    val rootTunRouteExcludeAddress: Preference<List<String>>
    val rootTunDnsMode: Preference<RootTunDnsMode>
    val rootTunFakeIpRange: Preference<String>
    val rootTunFakeIpRange6: Preference<String>
    val accessControlMode: Preference<AccessControlMode>
    val accessControlPackages: Preference<Set<String>>
    val accessControlShowSystemApps: Preference<Boolean>
    val accessControlSelectedFirst: Preference<Boolean>
}

/**
 * Contract for app settings consumed by runtime and feature modules.
 * Covers both runtime-facing properties and UI-facing preferences.
 */
interface AppSettingsReader {
    // Runtime-facing
    val automaticRestart: Preference<Boolean>
    val autoUpdateCurrentProfileOnStart: Preference<Boolean>
    val singleNodeTest: Preference<Boolean>
    val customUserAgent: Preference<String>
    val initialSetupCompleted: Preference<Boolean>
    val excludeFromRecents: Preference<Boolean>
    // UI-facing
    val privacyPolicyAccepted: Preference<Boolean>
    val themeMode: Preference<ThemeMode>
    val appLanguage: Preference<AppLanguage>
    val colorTheme: Preference<AppColorTheme>
    val themeAccentColorArgb: Preference<Long>
    val invertOnPrimaryColors: Preference<Boolean>
    val homePreviewGuideShown: Preference<Boolean>
    val hideAppIcon: Preference<Boolean>
    val showTrafficNotification: Preference<Boolean>
    val bottomBarAutoHide: Preference<Boolean>
    val topBarBlurEnabled: Preference<Boolean>
    val classicHomeEnabled: Preference<Boolean>
    val moeWallpaperUri: Preference<String>
    val moeWallpaperSourceUri: Preference<String>
    val moeWallpaperZoom: Preference<Float>
    val moeWallpaperBiasX: Preference<Float>
    val moeWallpaperBiasY: Preference<Float>
    val moeHomeQuote: Preference<String>
    val moeHomeQuoteAuthor: Preference<String>
    val moeSidebarExpanded: Preference<Boolean>
    val pageScale: Preference<Float>
    val logLevel: Preference<Int>
    val autoCheckAppUpdate: Preference<Boolean>
    val updateSourceKey: Preference<String>
    val webDav: WebDavSettingsReader
}

/** Read/write contract for WebDAV backup settings. */
interface WebDavSettingsReader {
    val webDavUrl: Preference<String>
    val webDavAccount: Preference<String>
    val webDavPassword: Preference<String>
    val webDavDir: Preference<String>
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
interface TrafficStatisticsRepository {
    fun getAppUsagesFlow(range: StatisticsTimeRange): Flow<List<AppTrafficUsage>>
    suspend fun getAppUsagesSorted(range: StatisticsTimeRange): List<AppTrafficUsage>
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
 * Contract for remote controller store consumed by runtime and feature modules.
 */
interface RemoteControllerStoreReader {
    val controllerEnabled: Preference<Boolean>
    val backends: Preference<List<RemoteBackend>>
    val activeBackendId: Preference<String>
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
    suspend fun readLogEntriesSince(fileName: String, sinceByteOffset: Long, maxEntries: Int = 2000): Pair<List<LogEntry>, Long>
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

// region Proxy runtime contracts (consumed by feature modules)

/** Priority level for proxy group synchronization scheduling. */
enum class ProxySyncPriority {
    OFF,
    SLOW,
    FAST,
}

/** Read-only contract for proxy group state and control actions consumed by feature modules. */
interface ProxyGroupRepository {
    val proxyGroups: StateFlow<List<ProxyGroupInfo>>
    suspend fun selectProxy(group: String, proxyName: String): Boolean
    suspend fun forceSelectProxy(group: String, proxyName: String): Boolean
    suspend fun refreshProxyGroups()
    suspend fun refreshProxyGroup(name: String, sort: ProxySort = ProxySort.Default)
    suspend fun healthCheck(group: String)
    suspend fun healthCheckAll()
    suspend fun healthCheckProxy(group: String, proxyName: String): Int
    fun warmUpProxyGroups()
    fun setProxyGroupSyncPriority(priority: ProxySyncPriority, source: String = "default")
}

/** Read-only contract for connection state and control consumed by feature modules. */
interface ConnectionRepository {
    val connectionSnapshot: StateFlow<ConnectionSnapshot>
    val isRunning: StateFlow<Boolean>
    suspend fun closeConnection(id: String): Boolean
    suspend fun closeAllConnections()
}

// endregion

// region Log contracts (consumed by runtime:service)

/** Contract for log record persistence consumed by runtime:service. */
interface LogRecordGateway {
    val isRecording: Boolean
    val currentLogFileName: String?
    val logPrefix: String
    val logSuffix: String
    val stopWaitMillis: Long

    fun start(application: android.app.Application)

    fun stop(application: android.app.Application)

    fun getLogDir(application: android.app.Application): java.io.File
}

/** Functional interface for writing a single log line to the runtime log. */
fun interface RuntimeLogWriter {
    fun writeLog(line: String)
}

// endregion

// region Service bootstrap contracts (consumed by runtime:service)

/**
 * Read-only contract for runtime:service bootstrap decisions.
 *
 * Android Services instantiated by the framework (AutoRestartService, ProxyTileService)
 * cannot use constructor injection. This interface abstracts the data-store queries they
 * need so that runtime:service does not depend on data-store implementations directly.
 */
interface ServiceBootstrapReader {
    val automaticRestart: Boolean
    val autoUpdateCurrentProfileOnStart: Boolean
    val proxyMode: ProxyMode
    fun isRemoteControllerActive(): Boolean
    fun consumePostUpdateColdStartPending(): Boolean
    fun markAutoStartStarted()
    fun clearAutoStart()
}

/**
 * Static holder for [ServiceBootstrapReader] so that Android framework-instantiated
 * components can access it without constructor injection.
 *
 * Must be initialized once in [android.app.Application.onCreate] before any
 * Service or TileService is created.
 */
object ServiceBootstrapHolder {
    @Volatile
    private var _reader: ServiceBootstrapReader? = null

    val reader: ServiceBootstrapReader
        get() = _reader
            ?: error("ServiceBootstrapReader not initialized; call initialize() in Application.onCreate")

    fun initialize(reader: ServiceBootstrapReader) {
        _reader = reader
    }
}

// region Runtime state readers (consumed by backup/restore in feature:settings)

/** Read/write contract for runtime service active profile state. */
interface ServiceStateReader {
    var activeProfile: java.util.UUID?
}

/** Read/write contract for profile records persisted by runtime:service. */
interface ProfileStoreReader {
    fun loadImported(): List<Imported>
    fun saveImported(list: List<Imported>)
    fun loadProfileOrder(): List<java.util.UUID>
    fun saveProfileOrder(order: List<java.util.UUID>)
}

// endregion
