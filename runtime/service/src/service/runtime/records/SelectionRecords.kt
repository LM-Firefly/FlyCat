package com.github.yumelira.yumebox.runtime.service.runtime.records

import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.model.Proxy
import com.github.yumelira.yumebox.core.model.ProxyGroup
import com.github.yumelira.yumebox.runtime.service.common.log.Log
import com.github.yumelira.yumebox.runtime.service.runtime.entity.Selection
import java.util.UUID
import kotlinx.coroutines.delay

object SelectionDao {
    private const val PIN_KEY_PREFIX = "pin_"

    fun persistForcePinnedSelection(
        profileUUID: UUID?,
        proxyGroup: String,
        requestedNode: String,
        patched: Boolean,
        supportsPinnedSelection: Boolean,
    ) {
        val uuid = profileUUID ?: return
        if (!patched) {
            removePinned(uuid, proxyGroup)
            return
        }
        if (!supportsPinnedSelection) {
            return
        }
        if (requestedNode.isBlank()) {
            removePinned(uuid, proxyGroup)
        } else {
            setPinned(uuid, proxyGroup, requestedNode)
        }
    }

    fun migrateLegacyIfNeeded() {
        ProfileStore.migrateLegacySelectionMemoryIfNeeded()
    }

    fun queryAll(): List<Selection> {
        migrateLegacyIfNeeded()
        return ProfileStore.loadSelections()
    }

    fun querySelections(profileUUID: UUID): List<Selection> {
        return queryAll().filter { it.uuid == profileUUID }
    }

    fun queryRestorableSelections(profileUUID: UUID): List<Selection> {
        return querySelections(profileUUID)
    }

    fun upsertManualSelection(profileUUID: UUID, groupName: String, selectedProxy: String) {
        upsertManualSelection(
            Selection(
                uuid = profileUUID,
                proxy = groupName.trim(),
                selected = selectedProxy.trim(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun upsertManualSelection(selection: Selection) {
        migrateLegacyIfNeeded()
        val normalized =
            selection.copy(
                proxy = selection.proxy.trim(),
                selected = selection.selected.trim(),
                updatedAt =
                    if (selection.updatedAt > 0L) selection.updatedAt
                    else System.currentTimeMillis(),
            )
        if (normalized.proxy.isEmpty() || normalized.selected.isEmpty()) {
            return
        }
        val list = ProfileStore.loadSelections().toMutableList()
        val index = list.indexOfFirst { it.uuid == normalized.uuid && it.proxy == normalized.proxy }
        if (index >= 0) {
            list[index] = normalized
        } else {
            list.add(normalized)
        }
        ProfileStore.saveSelections(list)
    }

    fun clear(profileUUID: UUID) {
        migrateLegacyIfNeeded()
        val list = ProfileStore.loadSelections().toMutableList()
        list.removeAll { it.uuid == profileUUID }
        ProfileStore.saveSelections(list)
        removeAllPins(profileUUID)
    }

    fun remove(profileUUID: UUID, proxy: String) {
        migrateLegacyIfNeeded()
        val list = ProfileStore.loadSelections().toMutableList()
        list.removeAll { it.uuid == profileUUID && it.proxy == proxy }
        ProfileStore.saveSelections(list)
    }

    fun setPinned(profileUUID: UUID, proxyGroup: String, pinnedNode: String) {
        val normalizedGroup = proxyGroup.trim()
        val normalizedNode = pinnedNode.trim()
        if (normalizedGroup.isEmpty() || normalizedNode.isEmpty()) return
        ProfileStore.setSelectionScopeValue(makePinKey(profileUUID, normalizedGroup), normalizedNode)
    }

    fun getPinned(profileUUID: UUID, proxyGroup: String): String? {
        val normalizedGroup = proxyGroup.trim()
        if (normalizedGroup.isEmpty()) return null
        return ProfileStore.getSelectionScopeValue(makePinKey(profileUUID, normalizedGroup))
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun removePinned(profileUUID: UUID, proxyGroup: String) {
        val normalizedGroup = proxyGroup.trim()
        if (normalizedGroup.isEmpty()) return
        ProfileStore.removeSelectionScopeValue(makePinKey(profileUUID, normalizedGroup))
    }

    fun getAllPins(profileUUID: UUID): Map<String, String> {
        val prefix = makePinKeyPrefix(profileUUID)
        return ProfileStore.querySelectionScopeValues(prefix)
            .mapNotNull { (key, value) ->
                val group = key.removePrefix(prefix).trim()
                val node = value.trim()
                if (group.isEmpty() || node.isEmpty()) null else group to node
            }
            .toMap()
    }

    private fun removeAllPins(profileUUID: UUID) {
        val prefix = makePinKeyPrefix(profileUUID)
        val keys = ProfileStore.querySelectionScopeValues(prefix).keys
        keys.forEach { scopedKey ->
            ProfileStore.removeSelectionScopeValue(scopedKey)
        }
    }

    private fun makePinKey(profileUUID: UUID, proxyGroup: String): String {
        return "${PIN_KEY_PREFIX}${profileUUID}_$proxyGroup"
    }

    private fun makePinKeyPrefix(profileUUID: UUID): String {
        return "${PIN_KEY_PREFIX}${profileUUID}_"
    }
}

internal object SelectionRestoreExecutor {
    private const val queryRetryCount = 3
    private const val queryRetryDelayMs = 150L

    suspend fun restore(
        profileUuid: UUID,
        selections: List<Selection>,
        pins: Map<String, String>,
        runtimeGroups: List<ProxyGroup>,
        tag: String,
    ) {
        val selectorGroups = runtimeGroups.associateBy { it.name }
        selections.forEach { selection ->
            val group =
                selectorGroups[selection.proxy]
                    ?: run {
                        removeSelection(profileUuid, selection, tag, "group missing")
                        return@forEach
                    }
            if (group.type != Proxy.Type.Selector) {
                removeSelection(profileUuid, selection, tag, "group not selector")
                return@forEach
            }

            val currentNodes =
                group.proxies.mapNotNull { proxy -> proxy.name.trim().takeIf { it.isNotEmpty() } }
            val targetNode = selection.selected.trim()
            if (targetNode.isEmpty() || targetNode !in currentNodes) {
                removeSelection(profileUuid, selection, tag, "node missing")
                return@forEach
            }

            if (!patchSelectorWithRetry(selection.proxy, targetNode)) {
                Log.w(
                    "$tag restore selector patch failed: profile=$profileUuid group=${selection.proxy} node=$targetNode"
                )
            }
        }

        pins.forEach { (groupName, pinnedNode) ->
            val group = selectorGroups[groupName] ?: run {
                removePinned(profileUuid, groupName, pinnedNode, tag, "group missing")
                return@forEach
            }
            if (!group.type.supportsPinnedSelection()) {
                removePinned(profileUuid, groupName, pinnedNode, tag, "group does not support pin")
                return@forEach
            }
            val currentNodes = group.proxies
                .mapNotNull { proxy -> proxy.name.trim().takeIf { it.isNotEmpty() } }
            val targetNode = pinnedNode.trim()
            if (targetNode.isEmpty() || targetNode !in currentNodes) {
                removePinned(profileUuid, groupName, pinnedNode, tag, "node missing")
                return@forEach
            }
            if (!patchForceSelectorWithRetry(groupName, targetNode)) {
                Log.w("$tag restore pin patch failed: profile=$profileUuid group=$groupName node=$targetNode")
            }
        }
    }

    private suspend fun patchSelectorWithRetry(group: String, node: String): Boolean {
        repeat(queryRetryCount) { attempt ->
            if (Clash.patchSelector(group, node)) {
                return true
            }
            if (attempt < queryRetryCount - 1) {
                delay(queryRetryDelayMs)
            }
        }
        return false
    }

    private suspend fun patchForceSelectorWithRetry(group: String, node: String): Boolean {
        repeat(queryRetryCount) { attempt ->
            if (Clash.patchForceSelector(group, node)) {
                return true
            }
            if (attempt < queryRetryCount - 1) {
                delay(queryRetryDelayMs)
            }
        }
        return false
    }

    private fun removeSelection(
        profileUuid: UUID,
        selection: Selection,
        tag: String,
        reason: String,
    ) {
        Log.w(
            "$tag remove invalid selector memory: profile=$profileUuid group=${selection.proxy} " +
                "node=${selection.selected} reason=$reason"
        )
        SelectionDao.remove(profileUuid, selection.proxy)
    }

    private fun removePinned(profileUuid: UUID, group: String, node: String, tag: String, reason: String) {
        Log.w(
            "$tag remove invalid pin memory: profile=$profileUuid group=$group node=$node reason=$reason",
        )
        SelectionDao.removePinned(profileUuid, group)
    }

    private fun String.supportsPinnedSelection(): Boolean {
        return this == Proxy.Type.URLTest || this == Proxy.Type.Fallback
    }
}
