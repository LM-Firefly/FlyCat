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

package com.github.yumelira.yumebox.screen.settings.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveManagerTest {
    private val manager = BackupArchiveManager()

    @Test
    fun writeThenReadArchiveKeepsManifestPayloadAndFiles() {
        val root = tempDir()
        val imported = root.resolve("imported").apply { mkdirs() }
        val overrides = root.resolve("overrides").apply { mkdirs() }
        val substore = root.resolve("substore").apply { mkdirs() }
        val wallpaper = root.resolve("wallpaper.dat")
        imported.resolve("profile.yaml").writeText("mixed-port: 7890")
        overrides.resolve("metadata.yaml").writeText("chains: []")
        substore.resolve("store.json").writeText("""{"items":[]}""")
        wallpaper.writeText("wallpaper")

        val archive =
            writeArchive(
                manifest = BackupManifest(appId = "com.example", appVersion = "1.0"),
                payload =
                    BackupPayload(
                        appSettings = AppSettingsBackup(customUserAgent = "YumeBox-Test"),
                        service = ServiceBackup(activeProfile = "00000000-0000-0000-0000-000000000001"),
                    ),
                files =
                    BackupArchiveManager.BackupFiles(
                        importedDir = imported,
                        overridesDir = overrides,
                        subStoreDataDir = substore,
                        moeWallpaperFile = wallpaper,
                    ),
            )

        val extracted = manager.readArchive(ByteArrayInputStream(archive), tempDir())

        assertEquals("com.example", extracted.manifest.appId)
        assertEquals("YumeBox-Test", extracted.payload.appSettings.customUserAgent)
        assertEquals(
            "00000000-0000-0000-0000-000000000001",
            extracted.payload.service.activeProfile,
        )
        assertEquals("mixed-port: 7890", extracted.importedDir.resolve("profile.yaml").readText())
        assertEquals("chains: []", extracted.overridesDir.resolve("metadata.yaml").readText())
        assertEquals("""{"items":[]}""", extracted.subStoreDataDir.resolve("store.json").readText())
        assertEquals("wallpaper", extracted.moeWallpaperFile.readText())
    }

    @Test
    fun missingManifestIsRejectedWithoutLeavingPayloadOnlyArchiveValid() {
        val archive = zipBytes("payload.json" to "{}")

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                manager.readArchive(ByteArrayInputStream(archive), tempDir())
            }

        assertTrue(error.message.orEmpty().contains("manifest", ignoreCase = true))
    }

    @Test
    fun missingPayloadIsRejected() {
        val archive = zipBytes("manifest.json" to """{"formatVersion":1}""")

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                manager.readArchive(ByteArrayInputStream(archive), tempDir())
            }

        assertTrue(error.message.orEmpty().contains("payload", ignoreCase = true))
    }

    @Test
    fun newerFormatVersionIsRejected() {
        val archive =
            zipBytes(
                "manifest.json" to """{"formatVersion":2}""",
                "payload.json" to "{}",
            )

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                manager.readArchive(ByteArrayInputStream(archive), tempDir())
            }

        assertTrue(error.message.orEmpty().contains("newer", ignoreCase = true))
    }

    @Test
    fun pathTraversalEntryIsRejected() {
        val target = tempDir()
        val outside = checkNotNull(target.parentFile).resolve("outside.txt")
        outside.delete()
        val archive =
            zipBytes(
                "manifest.json" to """{"formatVersion":1}""",
                "payload.json" to "{}",
                "../outside.txt" to "escape",
            )

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                manager.readArchive(ByteArrayInputStream(archive), target)
            }

        assertTrue(error.message.orEmpty().contains("unsafe", ignoreCase = true))
        assertFalse(outside.exists())
    }

    @Test
    fun replaceBackupDirectoryOverwritesExistingNestedFiles() {
        val root = tempDir()
        val source = root.resolve("source")
        val target = root.resolve("target")
        val nestedPath = "profile/providers/rules/spotify_domain.mrs"
        source.resolve(nestedPath).apply {
            parentFile?.mkdirs()
            writeText("new")
        }
        target.resolve(nestedPath).apply {
            parentFile?.mkdirs()
            writeText("old")
        }
        target.resolve("stale.txt").writeText("stale")

        replaceBackupDirectory(source, target)

        assertEquals("new", target.resolve(nestedPath).readText())
        assertFalse(target.resolve("stale.txt").exists())
    }

    private fun writeArchive(
        manifest: BackupManifest = BackupManifest(),
        payload: BackupPayload = BackupPayload(),
        files: BackupArchiveManager.BackupFiles =
            BackupArchiveManager.BackupFiles(
                importedDir = File("missing-imported"),
                overridesDir = File("missing-overrides"),
                subStoreDataDir = File("missing-substore"),
                moeWallpaperFile = File("missing-wallpaper"),
            ),
    ): ByteArray =
        ByteArrayOutputStream().use { output ->
            manager.writeArchive(output, manifest, payload, files)
            output.toByteArray()
        }

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, value) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(value.toByteArray())
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

    private fun tempDir(): File = kotlin.io.path.createTempDirectory("backup-test").toFile()
}
