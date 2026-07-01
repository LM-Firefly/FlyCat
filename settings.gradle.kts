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

@file:Suppress("UnstableApiUsage")


rootProject.name = "FlyCat"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        maven("https://jitpack.io")
        maven("https://maven.aliyun.com/nexus/content/repositories/releases/")

        maven("https://oom-maven.sawahara.host") {
            content {
                includeGroupAndSubgroups("ren.shiror")
                includeGroupAndSubgroups("work.niggergo")
                includeGroupAndSubgroups("dev.oom-wg")
            }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases")
        maven ("https://maven.aliyun.com/nexus/content/repositories/releases/")

        maven("https://oom-maven.sawahara.host") {
            content {
                includeGroupAndSubgroups("ren.shiror")
                includeGroupAndSubgroups("work.niggergo")
                includeGroupAndSubgroups("dev.oom-wg")
            }
        }
        maven("https://maven.kr328.app/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    ":core",
    ":platform",
    ":locale",
    ":ui",
    ":data",
    ":extension",
    ":app",
    ":lite",
    ":feature:update",
    ":feature:substore",
    ":feature:proxy",
    ":feature:override",
    ":feature:editor",
    ":feature:meta",
    ":runtime:api",
    ":runtime:client",
    ":runtime:service",
)
