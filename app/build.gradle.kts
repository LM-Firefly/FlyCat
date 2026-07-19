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

import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import dev.yume.packer.BuildLoaderDexTask
import dev.yume.packer.PackApkTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.google.devtools.ksp")
    id("com.mikepenz.aboutlibraries.plugin.android")
}

abstract class TransformPackedApksTask : PackApkTask() {
    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<TransformPackedApksTask>>

    @TaskAction
    fun transform() {
        val outputRoot = outputApkDirectory.get().asFile
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()
        transformationRequest.get().submit(this) { artifact ->
            val input = File(artifact.outputFile)
            val output = outputRoot.resolve(input.name)
            packApk(input, output)
            output
        }
    }
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
//  - geo.bundle=false   -> keep the XZ geo databases and BundleMRS.7z out of assets (the local
//    default); App.extractGeoFiles skips them and mihomo falls back to remote provider data.
// Release-only native-lib XZ compression is handled by the dev.yume.packer APK transform (which
// keeps libclash/libloader raw in nativeLibraryDir and packs the rest into assets/loader/), not here.
val buildAllAbis = providers.gradleProperty("build.allAbis").orNull?.toBoolean() ?: false
val geoBundle = providers.gradleProperty("geo.bundle").orNull?.toBoolean() ?: false
val splitAbiList = if (buildAllAbis) appAbiList else listOf("arm64-v8a")
val geoFilesAssetsDir = rootProject.layout.buildDirectory.dir("generated/assets/geo")
val signingPropertiesFile = rootProject.file("signing.properties")
val releaseSigningProperties = signingPropertiesFile.takeIf(File::isFile)?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

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
        manifestPlaceholders["applicationClass"] = ".App"
        manifestPlaceholders["componentFactory"] = "androidx.core.app.CoreComponentFactory"
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
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-loader.pro",
            )
            if (releaseSigningProperties != null) {
                manifestPlaceholders["applicationClass"] = "dev.yume.loader.LoaderApplication"
                manifestPlaceholders["componentFactory"] = "dev.yume.loader.LoaderComponentFactory"
            }
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
            excludes.add("tables/**")
            excludes.add("DebugProbesKt.bin")
            excludes.add("kotlin-tooling-metadata.json")
            excludes.add("**/*.kotlin_builtins")
            excludes.add("**/*.kotlin_module")
            excludes.add("**/*.properties")
            excludes.add("**/*.txt")
        }
    }

    signingConfigs {
        if (releaseSigningProperties != null) {
            create("release") {
                storeFile = rootProject.file("release.keystore")
                storePassword = releaseSigningProperties.getProperty("keystore.password")!!
                keyAlias = releaseSigningProperties.getProperty("key.alias")!!
                keyPassword = releaseSigningProperties.getProperty("key.password")!!
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

if (releaseSigningProperties != null) {
    val loaderRuntime = configurations.detachedConfiguration(
        dependencies.create("org.lsposed.hiddenapibypass:hiddenapibypass:6.1"),
    )
    androidComponents {
        onVariants(selector().withBuildType("release")) { variant ->
            val capitalized = variant.name.replaceFirstChar(Char::uppercaseChar)
            val loaderDexTask = tasks.register<BuildLoaderDexTask>("build${capitalized}LoaderDex") {
                group = "build"
                description = "Builds the standalone loader DEX for ${variant.name}"
                loaderAar.set(
                    project(":pack").layout.buildDirectory.file(
                        "outputs/aar/pack-release.aar"
                    )
                )
                runtimeArtifacts.from(loaderRuntime)
                sdkDirectory.set(sdkComponents.sdkDirectory)
                minSdk.set(variant.minSdk.apiLevel)
                outputDirectory.set(layout.buildDirectory.dir("intermediates/yumePacker/${variant.name}/loaderDex"))
                dependsOn(":pack:bundleReleaseAar")
            }
            val packApkTask = tasks.register<TransformPackedApksTask>("pack${capitalized}Apk") {
                group = "build"
                description = "Compresses DEX payloads and installs the loader in ${variant.name} APKs"
                loaderDex.set(loaderDexTask.flatMap { it.outputDirectory.file("classes.dex") })
                sdkDirectory.set(sdkComponents.sdkDirectory)
                originalApplication.set("com.github.yumelira.yumebox.App")
                originalComponentFactory.set("androidx.core.app.CoreComponentFactory")
                keyStoreFile.set(rootProject.layout.projectDirectory.file("release.keystore"))
                keyStorePassword.set(releaseSigningProperties.getProperty("keystore.password"))
                keyAlias.set(releaseSigningProperties.getProperty("key.alias"))
                keyPassword.set(releaseSigningProperties.getProperty("key.password"))
            }
            val artifactRequest = variant.artifacts.use(packApkTask)
                .wiredWithDirectories(
                    TransformPackedApksTask::inputApkDirectory,
                    TransformPackedApksTask::outputApkDirectory,
                )
                .toTransformMany(SingleArtifact.APK)
            packApkTask.configure {
                transformationRequest.set(artifactRequest)
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
    compileOnly(project(":pack"))

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
    implementation(libs.androidx.core.splashscreen)

    testImplementation("junit:junit:4.13.2")
}
