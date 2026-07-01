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

import com.android.build.gradle.tasks.PackageAndroidArtifact
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.Properties

plugins {
    id("com.android.application")
}

dependencies {
    implementation(libs.javet.node.android)
}

val projectApplicationId = providers.gradleProperty("project.applicationId")
    .orElse(providers.gradleProperty("project.namespace.base"))
    .get()
val updateUiBuildStamp = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
val updateUiCommitShort = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short=6", "HEAD")
        workingDir = rootDir
    }.standardOutput.asText.get().trim().ifBlank { "000000" }
}.getOrDefault("000000")
val updateUiBuildId = providers.gradleProperty("update.uiBuildId").orNull
    ?.trim()
    ?.ifEmpty { null }
    ?: "${updateUiBuildStamp}-${updateUiCommitShort}"

android {
    namespace = providers.gradleProperty("project.namespace.extension").get()

    defaultConfig {
        applicationId = "$projectApplicationId.extension"
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("android.targetSdk").get().toInt()
        versionCode = providers.gradleProperty("project.version.code").get().toInt()
        versionName = providers.gradleProperty("project.version.name").get()
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.apply {
                clear()
                add("jniLibs")
            }
        }
    }

    tasks.withType<PackageAndroidArtifact>().configureEach {
        doFirst { appMetadata.asFile.orNull?.writeText("") }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf("META-INF/**")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            val abiList = providers.gradleProperty("abi.extension.list").getOrElse("arm64-v8a,x86_64")
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            include(*abiList.toTypedArray())
            isUniversalApk = false
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
    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val abiName = output.filters.find {
                    it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
                }?.identifier ?: "universal"
                val buildTypeName = variant.buildType ?: "release"
                output.versionName.set(providers.gradleProperty("project.version.name").get())
                (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(
                    "extension-${abiName}-${buildTypeName}-${updateUiBuildId}.apk"
                )
            }
        }
    }
}
