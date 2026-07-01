package com.github.yumelira.yumebox.lite.config

import android.content.Context
import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.model.CompileRequest
import com.github.yumelira.yumebox.core.util.YamlCodec
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.runtime.client.ProfilesRepository
import com.github.yumelira.yumebox.core.importedDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TunProfileSync(
    private val context: Context,
    private val profilesRepository: ProfilesRepository,
    private val networkSettingsStore: NetworkSettingsStore,
) {
    suspend fun syncActiveProfile() =
        withContext(Dispatchers.IO) {
            val activeProfile = profilesRepository.queryActiveProfile()
            if (activeProfile == null) {
                applyRouteExcludeAddress(emptyList())
                return@withContext
            }

            val profileDir = context.importedDir.resolve(activeProfile.uuid.toString())
            val result =
                Clash.compilePreview(
                    CompileRequest(
                        profileUuid = activeProfile.uuid.toString(),
                        profileDir = profileDir.absolutePath,
                        profilePath = profileDir.resolve("config.yaml").absolutePath,
                        overrides = emptyList(),
                        outputPath = profileDir.resolve("runtime.yaml").absolutePath,
                    )
                )
            check(result.success) { result.error ?: "Lite tun profile preview failed" }

            val routeExcludeAddress = result.finalYaml.routeExcludeAddress()

            applyRouteExcludeAddress(routeExcludeAddress)
        }

    private fun applyRouteExcludeAddress(routeExcludeAddress: List<String>) {
        networkSettingsStore.tunRouteExcludeAddress.set(routeExcludeAddress)
        networkSettingsStore.bypassPrivateNetwork.set(false)
    }
}

private fun String.routeExcludeAddress(): List<String> {
    val root = runCatching { YamlCodec.loadMap(this) }.getOrDefault(emptyMap())
    val tun = root["tun"] as? Map<*, *> ?: return emptyList()
    val raw = tun["route-exclude-address"] as? List<*> ?: return emptyList()
    return raw.mapNotNull { item -> item?.toString()?.trim()?.takeIf(String::isNotEmpty) }
}
