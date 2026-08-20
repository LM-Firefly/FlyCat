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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            when {
                requested.group == "com.google.protobuf" -> useVersion(libs.versions.protobuf.get())
                requested.group == "org.bouncycastle" && requested.name == "bcprov-jdk18on" -> useVersion(libs.versions.bcprov.get())
                requested.group == "org.jdom" && requested.name == "jdom2" -> useVersion(libs.versions.jdom2.get())
                requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" -> useVersion(libs.versions.jose4j.get())
                requested.group == "com.fasterxml.jackson.core" && requested.name == "jackson-core" -> useVersion(libs.versions.jacksonCore.get())
                requested.group == "org.apache.commons" && requested.name == "commons-lang3" -> useVersion(libs.versions.commonsLang3.get())
                requested.group == "io.netty" -> useVersion(libs.versions.netty.get())
            }
        }
    }
}

plugins {
  `jvm-toolchains`
  id("com.android.application") apply false
  id("com.android.library") apply false
  alias(libs.plugins.kotlin.serialization) apply false
  id("org.jetbrains.kotlin.plugin.compose") apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.room) apply false
  alias(libs.plugins.aboutlibraries) apply false
  alias(libs.plugins.spotless) apply false
}

// AGP-created tool configurations (unified-test-platform-*, androidLintTool) and test classpaths resolve independently of the buildscript classpath above, so the same vulnerable-version floors must be enforced on project configurations as well.
allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            when {
                requested.group == "org.bouncycastle" && requested.name == "bcprov-jdk18on" -> useVersion(libs.versions.bcprov.get())
                requested.group == "io.netty" -> useVersion(libs.versions.netty.get())
                requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" -> useVersion(libs.versions.httpcomponentsHttpClient.get())
                requested.group == "org.apache.commons" && requested.name == "commons-lang3" -> useVersion(libs.versions.commonsLang3.get())
                requested.group == "com.google.guava" && requested.name == "guava" -> useVersion(libs.versions.guava.get())
                requested.group.startsWith("tools.jackson") -> useVersion(libs.versions.jackson3.get())
                requested.group == "com.tencent" && requested.name == "mmkv" -> useVersion(resolvedMmkvVersion)
            }
        }
    }
}

val androidCompileSdk = providers.gradleProperty("android.compileSdk").map(String::toInt).get()
val androidCompileSdkMinor = providers.gradleProperty("android.compileSdkMinor").map(String::toInt).orElse(0).get()
val androidMinSdk = providers.gradleProperty("android.minSdk").map(String::toInt).get()
val androidJvm = providers.gradleProperty("android.jvm").orElse(providers.gradleProperty("project.jvm")).orElse("21").get()
val androidJvmVersion = androidJvm.toInt()
val androidNdkVersion = providers.gradleProperty("android.ndkVersion").orNull.orEmpty()

// MMKV version selection: 64-bit for arm64/x86_64, 32-bit otherwise.
val mmkv64Version = libs.versions.mmkv64.get()
val mmkv32Version = libs.versions.mmkv32.get()
val injectedAbi = findProperty("android.injected.build.abi") as? String
val resolvedMmkvVersion = if (injectedAbi in listOf("arm64-v8a", "x86_64")) mmkv64Version else mmkv32Version
extra["mmkvVersion"] = resolvedMmkvVersion

subprojects {
    apply(plugin = "jvm-toolchains")

    val javaToolchainService = extensions.getByType(JavaToolchainService::class.java)

    tasks.withType<JavaCompile>().configureEach {
        javaCompiler.set(
            javaToolchainService.compilerFor {
                languageVersion.set(JavaLanguageVersion.of(androidJvmVersion))
            }
        )
    }

    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension>("android") {
            compileSdk = androidCompileSdk
            compileSdkMinor = androidCompileSdkMinor

            if (androidNdkVersion.isNotBlank()) {
                ndkVersion = androidNdkVersion
            }

            defaultConfig { minSdk = androidMinSdk }

            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(androidJvm)
                targetCompatibility = JavaVersion.toVersion(androidJvm)
            }

            sourceSets {
                getByName("main") {
                    kotlin.directories.apply {
                        clear()
                        add("src")
                    }
                    res.directories.apply {
                        clear()
                        add("res")
                    }
                    assets.directories.apply {
                        clear()
                        add("assets")
                    }
                    aidl.directories.apply {
                        clear()
                        add("aidl")
                    }
                    resources.directories.apply {
                        clear()
                        add("resources")
                    }
                    if (project.file("AndroidManifest.xml").isFile) {
                        manifest.srcFile("AndroidManifest.xml")
                    }
                }
            }
        }
    }

    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension>("android") {
            compileSdk = androidCompileSdk
            compileSdkMinor = androidCompileSdkMinor

            if (androidNdkVersion.isNotBlank()) {
                ndkVersion = androidNdkVersion
            }

            defaultConfig { minSdk = androidMinSdk }

            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(androidJvm)
                targetCompatibility = JavaVersion.toVersion(androidJvm)
            }

            buildFeatures { buildConfig = false }

            packaging { jniLibs { useLegacyPackaging = true } }

            sourceSets {
                getByName("main") {
                    kotlin.directories.apply {
                        clear()
                        add("src")
                    }
                    res.directories.apply {
                        clear()
                        add("res")
                    }
                    assets.directories.apply {
                        clear()
                        add("assets")
                    }
                    aidl.directories.apply {
                        clear()
                        add("aidl")
                    }
                    resources.directories.apply {
                        clear()
                        add("resources")
                    }
                    if (project.file("AndroidManifest.xml").isFile) {
                        manifest.srcFile("AndroidManifest.xml")
                    }
                }
            }
        }
    }
}

tasks.register("assembleReleaseWithExtension") {
    group = "build"
    description = "Assemble release APK with extension merged (arm64-v8a and x86_64, including javet libs)."
    dependsOn(":app:assembleRelease", ":extension:assembleRelease")
}
