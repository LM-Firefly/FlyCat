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

package com.github.lmfirefly.flycat.feature.log.domain

import com.github.lmfirefly.flycat.core.contract.LogStoreReader
import com.github.lmfirefly.flycat.core.model.LogEntry

/**
 * Manages incremental log reading with byte-offset tracking, truncation/rotation
 * detection, and a bounded entry cache (max 2000 entries per file).
 *
 * Extracted from LogViewModel to separate the incremental reading engine
 * from UI state management.
 */
class IncrementalLogReader(
    private val repository: LogStoreReader,
) {
    companion object {
        private const val MAX_CACHED_ENTRIES = 2000
    }

    /** Per-file byte offset tracker. */
    private val fileOffsets = mutableMapOf<String, Long>()
    /** Per-file accumulated entry cache. */
    private val fileEntryCache = mutableMapOf<String, ArrayDeque<LogEntry>>()

    /**
     * Incrementally reads log entries for [fileName]. On first call (or after [resetFile])
     * performs a full tail read; subsequent calls only read newly appended bytes.
     * Returns entries in reversed order (newest first) for display.
     */
    suspend fun readIncremental(fileName: String): List<LogEntry> {
        val sinceOffset = fileOffsets[fileName] ?: 0L
        val (newEntries, newOffset) = repository.readLogEntriesSince(fileName, sinceOffset)
        if (newOffset < sinceOffset) {
            // File was truncated/rotated — clear cache for this file
            fileEntryCache.remove(fileName)
        }
        if (newEntries.isNotEmpty()) {
            val cache = fileEntryCache.getOrPut(fileName) { ArrayDeque() }
            cache.addAll(newEntries)
            while (cache.size > MAX_CACHED_ENTRIES) { cache.removeFirst() }
        }
        fileOffsets[fileName] = newOffset
        return (fileEntryCache[fileName] ?: emptyList()).toList().asReversed()
    }

    /** Reset incremental cache for a specific file (e.g. after deletion). */
    fun resetFile(fileName: String) {
        fileOffsets.remove(fileName)
        fileEntryCache.remove(fileName)
    }

    /** Clear all caches (e.g. after deleting all logs). */
    fun resetAll() {
        fileOffsets.clear()
        fileEntryCache.clear()
    }
}
