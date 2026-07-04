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

plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
    id("com.mikepenz.aboutlibraries.plugin.android")
}


val appAbiList =
    gropify.abi.app.list.split(',').map { it.trim() }.filter { it.isNotEmpty() }

val geoFilesAssetsDir = rootProject.layout.buildDirectory.dir("generated/assets/geo")

// CI-computed build versioning. CI injects `-Pbuild.number=<N>` (N = ci-channel.yml
// run_number: +1 per run, one push -> one run, immune to git history rewrites; both
// channels of one run share the same N), `-Pbuild.hash=<commit sha>` and
// `-Pbuild.branch=<branch name>`; local builds fall back to the base version.
// versionName = <base>[.<branch>].<hash8> when a hash is injected. 5795 is the versionCode
// epoch that replaced the legacy manual `project.version.code` scheme (last manual value:
// 5200). If ci-channel.yml is ever RENAMED its run_number resets to 1 - bump this base
// above the last published versionCode to stay monotonic.
val baseVersionCode = 5795
val ciBuildNumber = providers.gradleProperty("build.number").orNull
    ?.trim()?.takeIf { it.isNotEmpty() }?.toInt()
val ciBuildHash = providers.gradleProperty("build.hash").orNull
    ?.trim()?.takeIf { it.isNotEmpty() }?.take(8)
// Branch segment normalization (must stay in sync with reusable-prepare-publish.yml):
// lowercase, every non-[a-z0-9] run collapses to a single '-', leading/trailing '-' trimmed.
val ciBuildBranch = providers.gradleProperty("build.branch").orNull
    ?.lowercase()
    ?.replace(Regex("[^a-z0-9]+"), "-")
    ?.trim('-')
    ?.takeIf { it.isNotEmpty() }
val appVersionCode = baseVersionCode + (ciBuildNumber ?: 0)
val appVersionName = ciBuildHash
    ?.let { hash -> listOfNotNull(gropify.project.version.name, ciBuildBranch, hash).joinToString(".") }
    ?: gropify.project.version.name

android {
    namespace = gropify.project.namespace.base

    defaultConfig {
        applicationId = gropify.project.namespace.base
        targetSdk = gropify.android.targetSdk
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "BASE_VERSION", "\"${gropify.project.version.name}\"")
        manifestPlaceholders["appName"] = gropify.project.name
    }

    compileOptions {
        val javaVer = gropify.android.jvm
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
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
            //noinspection WrongGradleMethod
            isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
            reset()
            // AGP Split.include only accepts vararg; copying this tiny ABI list is negligible.
            @Suppress("SpreadOperator")
            include(*appAbiList.toTypedArray())
            isUniversalApk = true
        }
    }

    packaging {
        jniLibs {
            excludes += listOf("lib/**/libjavet*.so")
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
                    "${gropify.project.name}-${appVersionName}-${abiName}-${buildTypeName}.apk"
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.animation)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":core"))
    implementation(project(":platform"))
    implementation(project(":locale"))
    implementation(project(":ui"))
    implementation(project(":data"))
    implementation(project(":runtime:api"))
    implementation(project(":runtime:client"))
    implementation(project(":runtime:service"))
    implementation(project(":feature:substore"))
    implementation(project(":feature:proxy"))
    implementation(project(":feature:override"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:meta"))

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
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    val mmkv64 = libs.versions.mmkv64.get()
    val mmkv32 = libs.versions.mmkv32.get()
    val injectedAbi = findProperty("android.injected.build.abi") as? String
    val mmkvVersion = if (injectedAbi in listOf("arm64-v8a", "x86_64")) mmkv64 else mmkv32
    //noinspection NewerVersionAvailable
    implementation("com.tencent:mmkv:$mmkvVersion")

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.timber)
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
