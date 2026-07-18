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

plugins {
    id("flycat-compose-library")
    id("ren.shiror.fyl.fytxt") version "2.+"
}

fytxt {
    packageName = "com.github.lmfirefly.flycat.locale"
    objectName = "FlyTxt"

    langSrcs = mapOf("Locale" to layout.projectDirectory.dir("lang"))
    langAliases = mapOf(
        "ZH_HANS" to "^ZH_.*(HANS|CN|SG)",
        "ZH" to "^ZH_(?!.*(HANS|CN|SG)).*"
    )
    defaultLang = "ZH_HANS"

    composeGen = true
    internalClass = false
    exportDeps = true
}

android {
    namespace = "com.github.lmfirefly.flycat.locale"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
