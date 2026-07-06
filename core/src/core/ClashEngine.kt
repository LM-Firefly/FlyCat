package com.github.yumelira.yumebox.core

import com.github.yumelira.yumebox.core.model.*
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ReceiveChannel

interface ClashEngine {
    // Compilation
    fun compilePreview(request: CompileRequest): CompileResult
    fun compileAndLoadConfigSummary(
        request: CompileRequest,
        completable: CompletableDeferred<Unit>,
    ): CompileRawSummary
    fun compileAndInspectGroups(
        request: CompileRequest,
        profileDir: File,
        excludeNotSelectable: Boolean,
    ): List<ProxyGroup>
    fun compileAndInspectTunRouteExcludeAddress(request: CompileRequest): List<String>
    // Lifecycle
    fun reset()
    fun forceGc()
    fun suspendCore(suspended: Boolean)
    // Tunnel state
    fun queryTunnelState(): TunnelState
    fun queryTrafficNow(): Traffic
    fun queryTrafficTotal(): Traffic
    // Connections
    fun queryConnections(): ConnectionSnapshot
    fun closeConnection(id: String): Boolean
    fun closeAllConnections()
    // System notifications
    fun notifyDnsChanged(dns: List<String>)
    fun notifyTimeZoneChanged(name: String, offset: Int)
    // TUN management
    fun startTun(
        fd: Int,
        stack: String,
        gateway: String,
        portal: String,
        dns: String,
        markSocket: (Int) -> Boolean,
        querySocketOwner: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress) -> String,
    )
    fun stopTun()
    // Root TUN management
    fun startRootTun(config: RootTunConfig): String?
    fun stopRootTun()
    // HTTP proxy
    fun startHttp(listenAt: String): String?
    fun stopHttp()
    // Proxy groups
    fun queryGroupNames(excludeNotSelectable: Boolean): List<String>
    fun inspectCompiledGroups(yamlText: String, profileDir: File, excludeNotSelectable: Boolean): List<ProxyGroup>
    fun inspectCompiledGroupNames(yamlText: String, excludeNotSelectable: Boolean): List<String>
    fun queryGroup(name: String, sort: ProxySort): ProxyGroup
    // Health checks
    fun healthCheck(name: String): CompletableDeferred<Unit>
    fun healthCheckProxy(proxyName: String): CompletableDeferred<String>
    fun healthCheckAll()
    // Configuration patching
    fun patchTunnelMode(mode: TunnelState.Mode): Boolean
    fun patchSelector(selector: String, name: String): Boolean
    fun patchForceSelector(selector: String, name: String): Boolean
    // Profile management
    fun fetchAndValid(
        path: File,
        url: String,
        force: Boolean,
        reportStatus: (FetchStatus) -> Unit,
    ): CompletableDeferred<Unit>
    // Providers
    fun queryProviders(): List<Provider>
    fun updateProvider(type: Provider.Type, name: String): CompletableDeferred<Unit>
    // Configuration
    fun queryConfiguration(): UiConfiguration
    // Logging
    fun subscribeLogcat(): ReceiveChannel<LogMessage>
    // Settings
    fun setCustomUserAgent(userAgent: String)
    fun setAgeSecretKey(key: String)
    fun genX25519KeyPair(): Pair<String, String>?
    fun toPublicKeys(secretKeys: List<String>): List<String>?
    fun verifySecretKeys(secretKeys: List<String>): Boolean
    fun verifyPublicKeys(publicKeys: List<String>): Boolean
}
