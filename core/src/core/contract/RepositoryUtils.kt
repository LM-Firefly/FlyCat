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

package com.github.yumelira.yumebox.core.contract

import android.content.Context
import com.github.yumelira.yumebox.core.model.AccessControlMode
import com.github.yumelira.yumebox.core.model.AppColorTheme
import com.github.yumelira.yumebox.core.model.AppIdentity
import com.github.yumelira.yumebox.core.model.AppLanguage
import com.github.yumelira.yumebox.core.model.AppTrafficUsage
import com.github.yumelira.yumebox.core.model.DailyTraffic
import com.github.yumelira.yumebox.core.model.TimeSlotTraffic
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
import com.github.yumelira.yumebox.core.model.RuntimeRule
import com.github.yumelira.yumebox.core.model.RunMode
import com.github.yumelira.yumebox.core.model.ProxySort
import com.github.yumelira.yumebox.core.model.ProxySortMode
import com.github.yumelira.yumebox.core.model.RemoteBackend
import com.github.yumelira.yumebox.core.model.TunDnsMode
import com.github.yumelira.yumebox.core.model.StatisticsTimeRange
import com.github.yumelira.yumebox.core.model.ThemeMode
import com.github.yumelira.yumebox.core.model.TunnelState.Mode
import com.github.yumelira.yumebox.core.model.TunStack
import com.github.yumelira.yumebox.core.model.UpdateProvidersResult
import com.github.yumelira.yumebox.core.model.UpdateSource
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

// ═══════════════════════════════════════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════════════════════════════════════

/** Utility functions for Repository layer to reduce boilerplate code. */
object RepositoryUtils {
    @Suppress("TooGenericExceptionCaught")
    suspend fun <T> safeApiCall(tag: String, operation: String, block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: Exception) { // fault barrier: any failure must become Result.failure
            if (error is CancellationException) throw error
            Timber.tag(tag).e(error, "Failed to execute $operation")
            Result.failure(error)
        }

    @Suppress("TooGenericExceptionCaught")
    fun <T> safeCall(tag: String, operation: String, block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: Exception) { // fault barrier: any failure must become Result.failure
            if (error is CancellationException) throw error
            Timber.tag(tag).e(error, "Failed to execute $operation")
            Result.failure(error)
        }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Language
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Contract for applying a language change at the application level.
 * Implemented by the app module and injected into data-layer controllers.
 */
fun interface LanguageApplier {
    fun apply(language: AppLanguage)
}

data class Preference<T>(val state: StateFlow<T>, private val update: (T) -> Unit, private val get: () -> T, private val refreshState: () -> Unit = { update(get()) }) {
    val value: T
        get() = get()
    fun set(value: T) = update(value)
    fun refresh() = refreshState()
}
fun <T> Preference<List<T>>.add(item: T) = set(value + item)
fun <T> Preference<List<T>>.remove(predicate: (T) -> Boolean) = set(value.filterNot(predicate))
fun <T> Preference<List<T>>.update(predicate: (T) -> Boolean, transform: (T) -> T) = set(value.map { if (predicate(it)) transform(it) else it })

// ═══════════════════════════════════════════════════════════════════════════════
// Override
// ═══════════════════════════════════════════════════════════════════════════════

/** Read/write contract for override config management. Implemented by [data] module. */
interface OverrideConfigRepository {
    suspend fun getUserConfigs(): List<OverrideConfig>
    fun getUserConfigsFlow(): Flow<List<OverrideConfig>>
    suspend fun getById(id: String): OverrideConfig?
    fun getConfigContent(id: String): String?
    fun saveConfigContent(id: String, content: String): Boolean
    suspend fun save(config: OverrideConfig)
    suspend fun delete(id: String): Boolean
    suspend fun duplicate(id: String): OverrideConfig?
    suspend fun reorderUserConfigs(orderedIds: List<String>)
    suspend fun loadCustomRoutingContent(): String?
    suspend fun saveCustomRoutingContent(content: String)
    /** Bundled templates, always listed above user imports. Materializes assets on first read. */
    suspend fun getBuiltInConfigs(): List<OverrideConfig>
}

/** Contract for applying overrides to the active profile. */
interface OverrideApplier {
    suspend fun reapplyActiveProfileOverride(): Boolean
    suspend fun reapplyActiveProfileIfUsingOverride(overrideId: String): Boolean
    suspend fun isActiveProfileUsingOverride(overrideId: String): Boolean
}

/** Read-only contract for profile-override bindings. */
interface ProfileBindingReader {
    fun getAllBindingsFlow(): Flow<List<ProfileBinding>>
    suspend fun getBinding(profileId: String): ProfileBinding?
    suspend fun setBinding(binding: ProfileBinding)
    suspend fun isOverrideInUse(overrideId: String): Boolean
    suspend fun getOverrideUsageCount(overrideId: String): Int
}

/** Contract for applying override configs to a specific profile. */
interface OverrideApplyExecutor {
    suspend fun applyOverride(profileId: String): Boolean
}

// ═══════════════════════════════════════════════════════════════════════════════
// Proxy & Connection
// ═══════════════════════════════════════════════════════════════════════════════

/** Read-only contract for proxy display settings consumed by feature modules. */
interface ProxyDisplaySettingsReader {
    val sortMode: Preference<ProxySortMode>
    val displayMode: Preference<ProxyDisplayMode>
    val proxyMode: Preference<Mode>
    val sheetHeightFraction: Preference<Float>
}

/** Contract for remote controller store consumed by runtime and feature modules. */
interface RemoteControllerStoreReader {
    val controllerEnabled: Preference<Boolean>
    val backends: Preference<List<RemoteBackend>>
    val activeBackendId: Preference<String>
    fun activeBackend(): RemoteBackend?
}

/** Priority level for proxy group synchronization scheduling. */
enum class ProxySyncPriority {
    OFF,
    SLOW,
    FAST,
}

/** Read-only contract for proxy group state and control actions. Implemented by [runtime:client]. */
interface ProxyGroupRepository {
    val proxyGroups: StateFlow<List<ProxyGroupInfo>>
    suspend fun selectProxy(group: String, proxyName: String): Boolean
    suspend fun forceSelectProxy(group: String, proxyName: String): Boolean
    suspend fun refreshProxyGroups(force: Boolean = false)
    suspend fun refreshProxyGroup(name: String, sort: ProxySort = ProxySort.Default)
    suspend fun healthCheck(group: String)
    suspend fun healthCheckAll()
    suspend fun healthCheckProxy(group: String, proxyName: String): Int
    fun warmUpProxyGroups()
    fun setProxyGroupSyncPriority(priority: ProxySyncPriority, source: String = "default")
}

/** Read-only contract for connection state and control. Implemented by [runtime:client]. */
interface ConnectionRepository {
    val connectionSnapshot: StateFlow<ConnectionSnapshot>
    val isRunning: StateFlow<Boolean>
    suspend fun queryConnections(): ConnectionSnapshot
    suspend fun queryConnectionsOverview(): com.github.yumelira.yumebox.core.model.ConnectionOverviewSnapshot
    suspend fun closeConnection(id: String): Boolean
    suspend fun closeAllConnections()
}

/** Read-only contract for runtime rules and temporary enable/disable control. */
interface RuntimeRuleRepository {
    suspend fun queryRules(): List<RuntimeRule>
    suspend fun setRuleDisabled(index: Int, disabled: Boolean): Boolean
}

// ═══════════════════════════════════════════════════════════════════════════════
// Service & Runtime
// ═══════════════════════════════════════════════════════════════════════════════

/** Contract for log record persistence consumed by [runtime:service]. */
interface LogRecordGateway {
    val isRecording: Boolean
    val currentLogFileName: String?
    val logPrefix: String
    val logSuffix: String
    val stopWaitMillis: Long
    fun start(application: android.app.Application)
    fun stop(application: android.app.Application)
    fun getLogDir(application: android.app.Application): File
}

/** Functional interface for writing a single log line to the runtime log. */
fun interface RuntimeLogWriter {
    fun writeLog(line: String)
}

/**
 * Read-only contract for [runtime:service] bootstrap decisions.
 * Android Services cannot use constructor injection; this interface abstracts the
 * data-store queries they need.
 */
interface ServiceBootstrapReader {
    val automaticRestart: Boolean
    val autoUpdateCurrentProfileOnStart: Boolean
    val runMode: RunMode
    fun isRemoteControllerActive(): Boolean
    fun consumePostUpdateColdStartPending(): Boolean
    fun markAutoStartStarted()
    fun clearAutoStart()
}

/**
 * Static holder for [ServiceBootstrapReader] so that Android framework-instantiated components can access it without constructor injection.
 */
object ServiceBootstrapHolder {
    @Volatile
    private var _reader: ServiceBootstrapReader? = null
    val reader: ServiceBootstrapReader
        get() = _reader ?: error("ServiceBootstrapReader not initialized; call initialize() in Application.onCreate")
    fun initialize(reader: ServiceBootstrapReader) { _reader = reader }
}

/** Read/write contract for runtime service active profile state. */
interface ServiceStateReader {
    var activeProfile: UUID?
}

/** Read/write contract for profile records persisted by [runtime:service]. */
interface ProfileStoreReader {
    fun loadImported(): List<Imported>
    fun saveImported(list: List<Imported>)
    fun loadProfileOrder(): List<UUID>
    fun saveProfileOrder(order: List<UUID>)
}

// ═══════════════════════════════════════════════════════════════════════════════
// Settings
// ═══════════════════════════════════════════════════════════════════════════════

/** Contract for network settings consumed by runtime and feature modules. */
interface NetworkSettingsReader {
    val runMode: Preference<RunMode>
    val bypassPrivateNetwork: Preference<Boolean>
    val dnsHijack: Preference<Boolean>
    val allowBypass: Preference<Boolean>
    val enableIPv6: Preference<Boolean>
    val systemProxy: Preference<Boolean>
    val disableAllOverride: Preference<Boolean>
    val tunStack: Preference<TunStack>
    val tunRouteExcludeAddress: Preference<List<String>>
    val tunIfName: Preference<String>
    val tunMtu: Preference<Int>
    val tunAutoRoute: Preference<Boolean>
    val tunStrictRoute: Preference<Boolean>
    val tunAutoRedirect: Preference<Boolean>
    val tunIncludeAndroidUser: Preference<List<Int>>
    val tunDnsMode: Preference<TunDnsMode>
    val tunFakeIpRange: Preference<String>
    val tunFakeIpRange6: Preference<String>
    val accessControlMode: Preference<AccessControlMode>
    val accessControlPackages: Preference<Set<String>>
    val accessControlShowSystemApps: Preference<Boolean>
    val accessControlSelectedFirst: Preference<Boolean>
    val wifiAutomationEnabled: Preference<Boolean>
    val wifiAutomationLocationRequested: Preference<Boolean>
    val wifiAutomationRules: Preference<List<com.github.yumelira.yumebox.core.model.WifiAutomationRule>>
    val wifiAutomationOtherWifiAction: Preference<com.github.yumelira.yumebox.core.model.WifiAutomationFallbackAction>
    val wifiAutomationNoWifiAction: Preference<com.github.yumelira.yumebox.core.model.WifiAutomationFallbackAction>
}

/** Contract for app settings consumed by runtime and feature modules. */
interface AppSettingsReader {
    val automaticRestart: Preference<Boolean>
    val autoUpdateCurrentProfileOnStart: Preference<Boolean>
    val customUserAgent: Preference<String>
    val initialSetupCompleted: Preference<Boolean>
    val excludeFromRecents: Preference<Boolean>
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
    val homeHitokotoEnabled: Preference<Boolean>
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

/** Read/write contract for feature store settings. */
interface FeatureStoreReader {
    fun consumePostUpdateColdStartPending(): Boolean
    val exitUiWhenBackground: Preference<Boolean>
    val showWebControlInProxy: Preference<Boolean>
    val isFirstOpen: Boolean
}

// ═══════════════════════════════════════════════════════════════════════════════
// Controller
// ═══════════════════════════════════════════════════════════════════════════════

/** Write contract for access control management with side effects. */
interface AccessControlControllerContract {
    fun setAccessControlMode(mode: AccessControlMode)
    fun applyPackages(packages: Set<String>)
}

/** Write contract for app settings with side effects. */
interface AppSettingsControllerContract {
    fun applyAppLanguage(language: AppLanguage)
    fun applyCustomUserAgent(userAgent: String)
}

/** Write contract for network settings with restart orchestration. */
interface NetworkSettingsControllerContract {
    fun setRunMode(mode: RunMode)
    fun <T> setAndRestartIfNeeded(preference: Preference<T>, value: T)
    suspend fun startService(mode: RunMode): Result<Unit>
    fun requestRestartIfRunning()
}

/** Contract for bulk MMKV store reset operations used during backup restore. */
interface BulkStoreReset {
    fun clearStore(id: String)
}

/** Contract for synchronizing all key-value store data to disk before process exit. */
interface StoreSynchronizer {
    fun syncAll()
}

// ═══════════════════════════════════════════════════════════════════════════════
// Store
// ═══════════════════════════════════════════════════════════════════════════════

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

// ═══════════════════════════════════════════════════════════════════════════════
// Backup & Sub-Store
// ═══════════════════════════════════════════════════════════════════════════════

/** Provides Sub-Store data paths and lifecycle control for backup/restore. */
interface SubStoreBackupSupport {
    val dataDir: File
    fun stopService(context: Context)
}

/** Provides navigation events from Sub-Store (e.g. open a URL). */
interface SubStoreNavigationHandler {
    val openUrlEvents: SharedFlow<String>
}

/** Lifecycle contract for the traffic statistics collector. */
interface TrafficCollectorContract : java.io.Closeable {
    fun stop()
}

/** Contract for backup export/restore operations. */
interface BackupDataSource {
    suspend fun exportBackup(output: OutputStream)
    suspend fun restoreBackup(input: InputStream)
    fun defaultBackupFileName(now: Long = System.currentTimeMillis()): String
}

/** Lifecycle hook for resources that must be cleaned up when the application terminates. */
fun interface AppShutdownHandler {
    fun onShutdown()
}
