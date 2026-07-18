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

import java.util.Properties
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
    id("com.mikepenz.aboutlibraries.plugin.android")
}


val appAbiList = providers.gradleProperty("abi.app.list").get().split(',').map { it.trim() }.filter { it.isNotEmpty() }

val geoFilesAssetsDir = rootProject.layout.buildDirectory.dir("generated/assets/geo")
val extensionAbiList = providers.gradleProperty("abi.extension.list").get().split(',').map { it.trim() }.filter { it.isNotEmpty() }
val withExtensionTaskRequested = gradle.startParameter.taskNames.any { taskName -> taskName.equals("assembleReleaseWithExtension", ignoreCase = true) || taskName.endsWith(":assembleReleaseWithExtension", ignoreCase = true) }
val withExtension = project.hasProperty("withExtension") || withExtensionTaskRequested
val packagingAbiList = if (withExtension) extensionAbiList else appAbiList
val projectApplicationId = providers.gradleProperty("project.applicationId").orElse(providers.gradleProperty("project.namespace.base")).get()
val updateRepository = providers.gradleProperty("update.repository").orNull
    ?.trim()?.ifEmpty { null } ?: "LM-Firefly/FlyCat"
val updateSource = providers.gradleProperty("update.source").orNull
    ?.trim()?.ifEmpty { null } ?: "smart"
val updateUiBuildId = providers.gradleProperty("update.uiBuildId").orNull
    ?.trim()?.ifEmpty { null }
    ?: run {
        val stamp = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
        val commit = runCatching {
            providers.exec {
                commandLine("git", "rev-parse", "--short=6", "HEAD")
                workingDir = rootDir
            }.standardOutput.asText.get().trim().ifBlank { "000000" }
        }.getOrDefault("000000")
        "$stamp-$commit"
    }
val updateMirrorTemplates = providers.gradleProperty("update.mirrorTemplates").orNull
    ?.trim()?.ifEmpty { null } ?: ""

// CI-injected build metadata used only for versionName composition.
val baseVersionName = providers.gradleProperty("project.version.name").get()
val ciBuildHash = providers.gradleProperty("build.hash").orNull
    ?.trim()?.takeIf { it.isNotEmpty() }?.take(8)
// Branch segment normalization (must stay in sync with reusable-prepare-publish.yml):
// lowercase, every non-[a-z0-9] run collapses to a single '-', leading/trailing '-' trimmed.
val ciBuildBranch = providers.gradleProperty("build.branch").orNull
    ?.lowercase()
    ?.replace(Regex("[^a-z0-9]+"), "-")
    ?.trim('-')
    ?.takeIf { it.isNotEmpty() }
val appVersionCode = updateUiBuildId.takeWhile { it.isDigit() }.take(8).toInt()
val appVersionName = ciBuildHash
    ?.let { hash -> listOfNotNull(baseVersionName, ciBuildBranch, hash).joinToString(".") }
    ?: baseVersionName

android {
    namespace = providers.gradleProperty("project.namespace.base").get()

    defaultConfig {
        applicationId = projectApplicationId
        targetSdk = providers.gradleProperty("android.targetSdk").get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
        manifestPlaceholders["appName"] = providers.gradleProperty("project.name").get()
        buildConfigField("String", "UPDATE_REPOSITORY", updateRepository.asBuildConfigString())
        buildConfigField("String", "UPDATE_SOURCE", updateSource.asBuildConfigString())
        buildConfigField("String", "UI_BUILD_ID", updateUiBuildId.asBuildConfigString())
        buildConfigField("String", "UPDATE_MIRROR_TEMPLATES", updateMirrorTemplates.asBuildConfigString())
    }

    compileOptions {
        val javaVer = providers.gradleProperty("android.jvm").get()
        sourceCompatibility = JavaVersion.toVersion(javaVer)
        targetCompatibility = JavaVersion.toVersion(javaVer)
        isCoreLibraryDesugaringEnabled = true
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
                addAll(
                    listOf(
                        "assets",
                        geoFilesAssetsDir.get().asFile.invariantSeparatorsPath,
                    )
                )
            }
            aidl.directories.apply {
                clear()
                add("aidl")
            }
            resources.directories.apply {
                clear()
                add("resources")
            }
            jniLibs.directories.apply {
                clear()
                add("../jniLibs")
            }
            if (project.file("AndroidManifest.xml").isFile) {
                manifest.srcFile("AndroidManifest.xml")
            }
        }
    }

    androidResources {
        generateLocaleConfig = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            optimization.enable = true
            vcsInfo.include = false
        }
    }

    splits {
        abi {
            //noinspection WrongGradleMethod
            isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
            reset()
            // AGP Split.include only accepts vararg; copying this tiny ABI list is negligible.
            @Suppress("SpreadOperator")
            include(*packagingAbiList.toTypedArray())
            isUniversalApk = true
        }
    }

    packaging {
        jniLibs {
            if (!withExtension) { excludes += listOf("lib/**/libjavet*.so") }
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        val keystore = rootProject.file("signing.properties")
        if (keystore.exists()) {
            create("release") {
                val prop = Properties().apply { keystore.inputStream().use(::load) }
                storeFile = rootProject.file("release.keystore")
                storePassword = prop.getProperty("keystore.password")!!
                keyAlias = prop.getProperty("key.alias")!!
                keyPassword = prop.getProperty("key.password")!!
            }
        }
    }

    if (signingConfigs.findByName("release") != null) {
        buildTypes.named("release").configure {
            signingConfig = signingConfigs.getByName("release")
        }
        buildTypes.named("debug").configure {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    //noinspection WrongGradleMethod
    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val abiName = output.filters.find {
                    it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
                }?.identifier ?: "universal"
                val buildTypeName = variant.buildType ?: "release"
                output.versionName.set(appVersionName)
                (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(
                    if (withExtension) {
                        "${providers.gradleProperty("project.name").get()}_Extension-${abiName}-${buildTypeName}-${updateUiBuildId}.apk"
                    } else {
                        "${providers.gradleProperty("project.name").get()}-${abiName}-${buildTypeName}-${updateUiBuildId}.apk"
                    }
                )
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":core"))
    implementation(project(":platform"))
    implementation(project(":locale"))
    implementation(project(":ui"))
    implementation(project(":data"))
    implementation(project(":runtime:api"))
    implementation(project(":runtime:client"))
    implementation(project(":runtime:service"))
    implementation(project(":feature:home"))
    implementation(project(":feature:log"))
    implementation(project(":feature:profiles"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:substore"))
    implementation(project(":feature:proxy"))
    implementation(project(":feature:override"))
    implementation(project(":feature:about"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:meta"))
    implementation(project(":feature:update"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur.android)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    //noinspection NewerVersionAvailable
    implementation("com.tencent:mmkv:${rootProject.extra["mmkvVersion"]}")

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.timber)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.xz)
    implementation(libs.smali.dexlib2) {
        exclude(group = "com.google.guava", module = "guava")
    }

    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.video)

    implementation(libs.sketch.compose)
    implementation(libs.sketch.http)
    implementation(libs.sketch.animated.gif)
    implementation(libs.sketch.animated.heif)
    implementation(libs.sketch.animated.webp)
    implementation(libs.sketch.animated.gif.koral)

    implementation(libs.reorderable)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hiddenapibypass)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
}
