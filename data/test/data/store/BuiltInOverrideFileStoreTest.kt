/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * YumeBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with YumeBox. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

package com.github.yumelira.yumebox.data.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BuiltInOverrideFileStoreTest {
    @Test
    fun syncReplacesStaleMaterializedBuiltIn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val overridesDir = Files.createTempDirectory("built-in-override-test").toFile()
        val configsDir = overridesDir.resolve("configs").apply { mkdirs() }
        val id = "builtin-add-direct-rules"
        val target = configsDir.resolve("$id.yaml")
        val staleExtension = configsDir.resolve("$id.js")
        target.writeText("rules:\n  - MATCH,OLD\n")
        staleExtension.writeText("old")

        val synchronized = BuiltInOverrideFileStore(context, overridesDir).sync(id)
        val packaged =
            context.assets.open("overrides/builtin/add_direct_rules.yaml").bufferedReader().use {
                it.readText()
            }

        assertEquals(target.canonicalFile, synchronized?.canonicalFile)
        assertEquals(packaged, target.readText())
        assertTrue(target.readText().contains("rules-start:"))
        assertFalse(staleExtension.exists())

        target.writeText("rules:\n  - MATCH,STALE_AGAIN\n")
        BuiltInOverrideFileStore(context, overridesDir).sync(id)
        assertEquals(packaged, target.readText())
    }
}
