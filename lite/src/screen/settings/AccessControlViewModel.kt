package com.github.yumelira.yumebox.screen.settings

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.core.model.AccessControlMode
import com.github.yumelira.yumebox.data.controller.AccessControlController
import com.github.yumelira.yumebox.data.store.AppStateManager
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.runtime.client.ProxyFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccessControlViewModel(
    application: Application,
    appStateManager: AppStateManager,
    private val controller: AccessControlController,
    private val proxyFacade: ProxyFacade,
) : AndroidViewModel(application) {
    private val settings: NetworkSettingsStore = appStateManager.networkSettingsStore

    data class AppInfo(
        val packageName: String,
        val label: String,
        val isSystemApp: Boolean,
        val isChinaApp: Boolean,
        val installTime: Long,
        val updateTime: Long,
    )

    enum class SortMode {
        Label,
        PackageName,
        InstallTime,
        UpdateTime;

        val displayName: String
            get() =
                when (this) {
                    Label -> "名称"
                    PackageName -> "包名"
                    InstallTime -> "安装时间"
                    UpdateTime -> "更新时间"
                }
    }

    data class UiState(
        val isLoading: Boolean = true,
        val apps: List<AppInfo> = emptyList(),
        val selectedPackages: Set<String> = emptySet(),
        val searchQuery: String = "",
        val showSystemApps: Boolean = false,
        val sortMode: SortMode = SortMode.Label,
        val selectedFirst: Boolean = true,
        val needsMiuiPermission: Boolean = false,
    )

    private val uiStateFlow = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = uiStateFlow.asStateFlow()
    val accessControlMode = settings.accessControlMode

    val filteredApps: StateFlow<List<AppInfo>> =
        uiStateFlow
            .map { state ->
                filterApps(
                    apps = state.apps,
                    selectedPackages = state.selectedPackages,
                    query = state.searchQuery,
                    showSystemApps = state.showSystemApps,
                    sortMode = state.sortMode,
                    selectedFirst = state.selectedFirst,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkAndLoad()
    }

    fun onPermissionResult() {
        uiStateFlow.update { it.copy(needsMiuiPermission = false) }
        loadApps()
    }

    fun onAccessControlModeChange(mode: AccessControlMode) {
        controller.setAccessControlMode(mode)
    }

    fun onSearchQueryChange(query: String) {
        uiStateFlow.update { it.copy(searchQuery = query) }
    }

    fun onShowSystemAppsChange(show: Boolean) {
        uiStateFlow.update { it.copy(showSystemApps = show) }
        settings.accessControlShowSystemApps.set(show)
    }

    fun onSelectedFirstChange(selectedFirst: Boolean) {
        uiStateFlow.update { it.copy(selectedFirst = selectedFirst) }
        settings.accessControlSelectedFirst.set(selectedFirst)
    }

    fun onSortModeChange(sortMode: SortMode) {
        uiStateFlow.update { it.copy(sortMode = sortMode) }
    }

    fun onAppSelectionChange(packageName: String, selected: Boolean) {
        uiStateFlow.update { state ->
            state.copy(
                selectedPackages = state.selectedPackages.updatePackage(packageName, selected)
            )
        }
        persistSelectionAndApply()
    }

    fun selectAll() {
        val packageSet = filteredApps.value.mapTo(linkedSetOf()) { it.packageName }
        uiStateFlow.update { state ->
            state.copy(selectedPackages = state.selectedPackages + packageSet)
        }
        persistSelectionAndApply()
    }

    fun deselectAll() {
        val packageSet = filteredApps.value.mapTo(linkedSetOf()) { it.packageName }
        uiStateFlow.update { state ->
            state.copy(selectedPackages = state.selectedPackages - packageSet)
        }
        persistSelectionAndApply()
    }

    fun invertSelection() {
        val packageSet = filteredApps.value.mapTo(linkedSetOf()) { it.packageName }
        uiStateFlow.update { state ->
            val selectedPackages = state.selectedPackages.toMutableSet()
            packageSet.forEach { pkg ->
                if (!selectedPackages.add(pkg)) {
                    selectedPackages.remove(pkg)
                }
            }
            state.copy(selectedPackages = selectedPackages)
        }
        persistSelectionAndApply()
    }

    fun selectChinaAppsInCurrentList(): Int {
        return applyRegionalSelectionInCurrentList(selectChina = true)
    }

    fun selectNonChinaAppsInCurrentList(): Int {
        return applyRegionalSelectionInCurrentList(selectChina = false)
    }

    fun exportPackages(): String {
        return uiStateFlow.value.selectedPackages.joinToString("\n")
    }

    fun importPackages(text: String): Int {
        val packages = text.lines().map(String::trim).filter(String::isNotEmpty).toSet()

        val validPackages =
            packages.intersect(uiStateFlow.value.apps.mapTo(linkedSetOf()) { it.packageName })
        uiStateFlow.update { state ->
            state.copy(selectedPackages = state.selectedPackages + validPackages)
        }
        persistSelectionAndApply()
        return validPackages.size
    }

    private fun checkAndLoad() {
        val context = getApplication<Application>()
        val permission = "com.android.permission.GET_INSTALLED_APPS"

        if (proxyFacade.hasRootPackageAccess()) {
            loadApps()
            return
        }

        val hasPermission =
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            loadApps()
            return
        }

        val isMiuiPermission =
            runCatching {
                    context.packageManager.getPermissionInfo(permission, 0).packageName ==
                        "com.lbe.security.miui"
                }
                .getOrDefault(false)

        if (isMiuiPermission) {
            uiStateFlow.update { it.copy(isLoading = false, needsMiuiPermission = true) }
        } else {
            loadApps()
        }
    }

    private fun loadApps() {
        viewModelScope.launch {
            uiStateFlow.update { it.copy(isLoading = true) }
            val selectedPackages = settings.accessControlPackages.value
            val showSystemApps = settings.accessControlShowSystemApps.value
            val selectedFirst = settings.accessControlSelectedFirst.value
            val apps =
                try {
                    withContext(Dispatchers.IO) { loadInstalledApps() }
                } catch (_: Exception) {
                    uiStateFlow.update { state ->
                        state.copy(isLoading = false, needsMiuiPermission = true)
                    }
                    return@launch
                }

            uiStateFlow.update {
                it.copy(
                    isLoading = false,
                    apps = apps,
                    selectedPackages = selectedPackages,
                    showSystemApps = showSystemApps,
                    selectedFirst = selectedFirst,
                    needsMiuiPermission = false,
                )
            }
        }
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val application = getApplication<Application>()
        val pm = application.packageManager
        val selfPackageName = application.packageName

        val appInfos =
            runCatching { pm.getInstalledApplications(PackageManager.GET_META_DATA) }
                .getOrElse { error ->
                    if (error is SecurityException) {
                        loadInstalledAppsFromRoot(pm, selfPackageName)
                    } else {
                        throw error
                    }
                }

        return appInfos
            .filterNot { it.packageName == selfPackageName }
            .map { appInfo ->
                val packageInfo =
                    runCatching { pm.getPackageInfo(appInfo.packageName, 0) }.getOrNull()
                AppInfo(
                    packageName = appInfo.packageName,
                    label = appInfo.loadLabel(pm).toString(),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isChinaApp = isChinaPackage(appInfo.packageName),
                    installTime = packageInfo?.firstInstallTime ?: 0L,
                    updateTime = packageInfo?.lastUpdateTime ?: 0L,
                )
            }
    }

    private fun loadInstalledAppsFromRoot(
        pm: PackageManager,
        selfPackageName: String,
    ): List<ApplicationInfo> {
        val packageNames =
            proxyFacade.queryInstalledRootPackageNames()
                ?: throw SecurityException("Unable to query installed packages from root shell")

        return packageNames
            .asSequence()
            .filterNot { it == selfPackageName }
            .mapNotNull { packageName ->
                runCatching { pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA) }
                    .getOrNull()
            }
            .toList()
    }

    private fun filterApps(
        apps: List<AppInfo>,
        selectedPackages: Set<String>,
        query: String,
        showSystemApps: Boolean,
        sortMode: SortMode,
        selectedFirst: Boolean,
    ): List<AppInfo> {
        val filtered = apps.filter { app ->
            val matchesQuery =
                query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            val matchesSystem = showSystemApps || !app.isSystemApp
            matchesQuery && matchesSystem
        }

        val comparator =
            when (sortMode) {
                SortMode.Label -> compareBy<AppInfo> { it.label.lowercase() }
                SortMode.PackageName -> compareBy { it.packageName.lowercase() }
                SortMode.InstallTime -> compareByDescending { it.installTime }
                SortMode.UpdateTime -> compareByDescending { it.updateTime }
            }

        val sorted = filtered.sortedWith(comparator)
        return if (selectedFirst) {
            sorted.sortedByDescending { selectedPackages.contains(it.packageName) }
        } else {
            sorted
        }
    }

    private fun applyRegionalSelectionInCurrentList(selectChina: Boolean): Int {
        val currentFiltered = filteredApps.value
        val currentPackages = currentFiltered.mapTo(linkedSetOf()) { it.packageName }
        val targetPackages =
            currentFiltered
                .filter { it.isChinaApp == selectChina }
                .mapTo(linkedSetOf()) { it.packageName }

        uiStateFlow.update { state ->
            state.copy(
                selectedPackages =
                    state.selectedPackages.minus(currentPackages).plus(targetPackages)
            )
        }
        persistSelectionAndApply()
        return targetPackages.size
    }

    private fun persistSelectionAndApply() {
        controller.applyPackages(uiStateFlow.value.selectedPackages)
    }

    private fun isChinaPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        skipPrefixList.forEach {
            if (normalized == it || normalized.startsWith("$it.")) {
                return false
            }
        }
        if (
            normalized.startsWith("cn.") ||
                normalized.contains(".cn.") ||
                normalized.endsWith(".cn")
        ) {
            return true
        }
        return normalized.matches(chinaAppRegex)
    }

    companion object {
        private fun Set<String>.updatePackage(packageName: String, selected: Boolean): Set<String> {
            return if (selected) this + packageName else this - packageName
        }

        private val skipPrefixList =
            listOf(
                "com.google",
                "com.android.chrome",
                "com.android.vending",
                "com.microsoft",
                "com.apple",
                "com.zhiliaoapp.musically",
                "com.android.providers.downloads",
            )

        private val chinaAppPrefixList =
            listOf(
                "com.tencent",
                "com.alibaba",
                "com.alipay",
                "com.amap",
                "com.sina",
                "com.weibo",
                "com.sankuai",
                "com.dianping",
                "com.jingdong",
                "com.xunmeng",
                "com.xingin",
                "com.zhihu",
                "com.bilibili",
                "com.coolapk",
                "tv.danmaku",
                "com.kuaishou",
                "com.smile.gifmaker",
                "com.ss.android",
                "com.qiyi",
                "com.youku",
                "com.sohu",
                "com.netease",
                "com.baidu",
                "com.mi",
                "com.xiaomi",
                "com.huawei",
                "com.vivo",
                "com.oppo",
                "com.oneplus",
                "me.ele",
                "ctrip",
                "com.taobao",
                "com.tmall",
            )

        private val chinaAppRegex =
            Regex("^(" + chinaAppPrefixList.joinToString("|") { Regex.escape(it) } + ")(\\..*)?$")
    }
}
