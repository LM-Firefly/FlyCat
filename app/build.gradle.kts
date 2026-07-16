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

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}


val appAbiList =
    gropify.abi.app.list.split(',').map { it.trim() }.filter { it.isNotEmpty() }

// Packaging matrix switches. CLI -P properties (same pattern as build.number below), NOT
// gropify keys, so CI can flip them per invocation:
//  - build.allAbis=true -> per-ABI splits for the full abi.app.list plus a universal APK
//    (CI). Default (local dev) builds only arm64-v8a and skips the universal APK.
//  - geo.bundle=false   -> keep the XZ geo databases out of assets (the local default);
//    App.extractGeoFiles skips them and the mihomo core downloads each database on first use.
val buildAllAbis = providers.gradleProperty("build.allAbis").orNull?.toBoolean() ?: false
val geoBundle = providers.gradleProperty("geo.bundle").orNull?.toBoolean() ?: false
val splitAbiList = if (buildAllAbis) appAbiList else listOf("arm64-v8a")
val geoFilesAssetsDir = rootProject.layout.buildDirectory.dir("generated/assets/geo")

// CI-computed build versioning. CI injects `-Pbuild.number=<N>` where N is the commit
// count of the built commit's parent chain (`git rev-list --count HEAD`, computed inside
// reusable-build-apk-only.yml / reusable-prepare-publish.yml after a fetch-depth:0
// checkout), plus `-Pbuild.hash=<commit sha>` and `-Pbuild.branch=<branch name>`; local
// builds fall back to the epoch alone. versionCode = epoch + N, where the epoch is
// `project.version.code` from gradle.properties (do not hardcode it here). Channel, PR and
// official release builds all share this one formula, so every published APK gets a unique,
// comparable versionCode (releases are no longer stuck at the bare epoch below channel
// packages). Monotonic vs the retired ci-channel run_number scheme: the commit count only
// grows along a branch and is >= the number of pushes >= run_number, so the new sequence
// never sorts below the already-published epoch+run_number packages; if history is ever
// rewritten so the count shrinks, bump project.version.code above the last published
// versionCode instead. versionName = <base>[.<branch>].<hash8> only when a hash is
// injected - official releases pass no hash/branch, so they keep the clean base version
// (e.g. 0.5.2) while still getting a unique versionCode.
val baseVersionCode = gropify.project.version.code
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

// Published APK file names are produced directly by Gradle. CI supplies the tail and
// optional channel segment once per workflow run; local builds omit both.
val apkOutputPrefix = providers.gradleProperty("apk.output.prefix").orNull
    ?.trim()?.takeIf { it.isNotEmpty() } ?: gropify.project.name
val apkOutputTail = providers.gradleProperty("apk.output.tail").orNull
    ?.trim()?.takeIf { it.isNotEmpty() }
val apkChannelSegment = providers.gradleProperty("apk.output.channel").orNull
    ?.trim()?.takeIf { it.isNotEmpty() }
val apkGeoSegment = if (geoBundle) "builtin" else "external"

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
                add("assets")
                if (geoBundle) {
                    add(geoFilesAssetsDir.get().asFile.invariantSeparatorsPath)
                }
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
        getByName("test") {
            kotlin.directories.apply {
                clear()
                add("test")
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
            //noinspection ChromeOsAbiSupport
            include(*splitAbiList.toTypedArray())
            isUniversalApk = buildAllAbis
        }
    }

    packaging {
        jniLibs {
            excludes += listOf("lib/**/libjavet*.so")
            useLegacyPackaging = true
        }
        resources {
            excludes.add("META-INF/**")
            excludes.add("okhttp3/**")
            excludes.add("schema/**")
            excludes.add("assets/dexopt/**")
            excludes.add("DebugProbesKt.bin")
            excludes.add("kotlin-tooling-metadata.json")
            excludes.add("**/*.kotlin_builtins")
            excludes.add("**/*.kotlin_module")
            excludes.add("**/*.properties")
            excludes.add("**/*.txt")
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
                val outputName = buildList {
                    add(apkOutputPrefix)
                    add(apkGeoSegment)
                    if (abiName != "arm64-v8a") add(abiName)
                    apkChannelSegment?.let(::add)
                    apkOutputTail?.let(::add)
                }.joinToString("-") + ".apk"
                output.versionName.set(appVersionName)
                (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(
                    outputName
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

    testImplementation("junit:junit:4.13.2")
}
