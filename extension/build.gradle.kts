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

@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.tasks.PackageAndroidArtifact
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.Properties
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

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
        versionCode = updateUiBuildId.takeWhile { it.isDigit() }.take(8).toInt()
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
            // AGP Split.include only accepts vararg; copying this tiny ABI list is negligible.
            @Suppress("SpreadOperator")
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
            val outputNamesByAbi = mutableMapOf<String, String>()
            variant.outputs.forEach { output ->
                val abiName = output.filters.find {
                    it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
                }?.identifier ?: error("Extension output must carry an ABI split filter")
                val buildTypeName = variant.buildType ?: "release"
                output.versionName.set(providers.gradleProperty("project.version.name").get())
                val outputName = "extension-${abiName}-${buildTypeName}-${updateUiBuildId}.apk"
                (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(
                    outputName
                )
                outputNamesByAbi[abiName] = outputName
            }
            // 产物契约（自 kotlin-toolchain 打包管线回移）：自检 + .apk.json 身份边车 + 原子发布到 output_apk/<buildType>/。
            // extension 只产 64 位 ABI，由 mmkv64 线的那一轮发布（mmkv32 线一轮为空属正常）。
            val capitalized = variant.name.replaceFirstChar(Char::uppercaseChar)
            val mmkv64Abis = rootProject.extra["mmkv64Abis"] as Set<String>
            val mmkvLine64 = rootProject.extra["mmkvLine"] == "64"
            val extensionAbis = providers.gradleProperty("abi.extension.list").getOrElse("arm64-v8a,x86_64").split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val signing = resolveSigningIdentity(project)
            tasks.register<BuildApkPublishTask>("publish${capitalized}Apks") {
                group = "distribution"
                description = "Verifies ${variant.name} APKs and publishes them with .apk.json sidecars to output_apk/${variant.name} for the active MMKV line"
                apkDir.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
                outputNames.putAll(outputNamesByAbi)
                publishAbis.set(outputNamesByAbi.keys.filter { (it in mmkv64Abis) == mmkvLine64 }.toSet())
                universalAbis.set(extensionAbis)
                requiredNativePatterns.set(listOf("libjavet*.so"))
                applicationId.set("$projectApplicationId.extension")
                versionCode.set(updateUiBuildId.takeWhile { it.isDigit() }.take(8).toInt().toString())
                versionName.set(providers.gradleProperty("project.version.name").get())
                moduleName.set("extension")
                variantName.set(variant.name)
                mmkvVersion.set("none")
                buildToolsDir.set(layout.dir(provider { resolveBuildToolsDir(project) }))
                if (signing != null) {
                    signingStore.set(signing.store)
                    signingAlias.set(signing.alias)
                    signingStorePassword.set(signing.storePassword)
                    signingKeyPassword.set(signing.keyPassword)
                }
                publishDir.set(rootProject.layout.projectDirectory.dir("output_apk/${variant.name}"))
            }
        }
    }
}

tasks.register<Sync>("collectJavetNative") {
    group = "distribution"
    description = "Extracts Javet native libraries for arm64-v8a and x86_64"
    from(provider { configurations.getByName("releaseRuntimeClasspath").files.map(::zipTree) }) {
        include("jni/arm64-v8a/libjavet-node-android.v.${libs.versions.javetNodeAndroid.get()}.so")
        rename { "libjavet-arm64-v8a.so" }
        eachFile { relativePath = RelativePath(true, name) }
        includeEmptyDirs = false
    }
    from(provider { configurations.getByName("releaseRuntimeClasspath").files.map(::zipTree) }) {
        include("jni/x86_64/libjavet-node-android.v.${libs.versions.javetNodeAndroid.get()}.so")
        rename { "libjavet-x86_64.so" }
        eachFile { relativePath = RelativePath(true, name) }
        includeEmptyDirs = false
    }
    into(rootProject.layout.buildDirectory.dir("generated/libjavet"))
}

tasks.register("compressJavetNative") {
    group = "distribution"
    description = "Compresses Javet native libraries as XZ release assets"
    dependsOn("collectJavetNative")
    val dir = File(rootProject.layout.buildDirectory.get().asFile, "generated/libjavet")
    val abis = listOf("arm64-v8a", "x86_64")
    abis.forEach { abi ->
        val f = File(dir, "libjavet-${abi}.so")
        inputs.file(f)
        outputs.file(File(dir, "libjavet-${abi}.so.xz"))
    }
    doLast {
        abis.forEach { abi ->
            val src = File(dir, "libjavet-${abi}.so")
            val dst = File(dir, "libjavet-${abi}.so.xz")
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { output ->
                    XZOutputStream(output, LZMA2Options(9)).use { xz ->
                        input.copyTo(xz, bufferSize = 1 shl 16)
                    }
                }
            }
        }
    }
}
