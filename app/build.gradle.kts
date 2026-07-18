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

import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import dev.flycat.packer.BuildLoaderDexTask
import dev.flycat.packer.PackApkTask
import java.io.File
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.Properties
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
fun escapeBuildConfigString(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
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


val appAbiList = providers.gradleProperty("abi.app.list").get().split(',').map { it.trim() }.filter { it.isNotEmpty() }
val buildAllAbis = providers.gradleProperty("build.allAbis").orNull?.toBoolean() ?: false

val geoFilesAssetsDir = rootProject.layout.buildDirectory.dir("generated/assets/geo")
val signingPropertiesFile = rootProject.file("signing.properties")
val releaseSigningProperties = signingPropertiesFile.takeIf(File::isFile)?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}
val extensionAbiList = providers.gradleProperty("abi.extension.list").get().split(',').map { it.trim() }.filter { it.isNotEmpty() }
val withExtensionTaskRequested = gradle.startParameter.taskNames.any { taskName -> taskName.equals("assembleReleaseWithExtension", ignoreCase = true) || taskName.endsWith(":assembleReleaseWithExtension", ignoreCase = true) }
val withExtension = project.hasProperty("withExtension") || withExtensionTaskRequested
// GeoFiles are always bundled; decoupled from withExtension.
val geoBundle = true
val splitAbiList = if (withExtension) extensionAbiList else if (buildAllAbis) appAbiList else listOf("arm64-v8a")
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

// Published APK file names are produced directly by Gradle. CI supplies the tail and
// optional channel segment once per workflow run; local builds omit both.
val apkOutputPrefix = providers.gradleProperty("apk.output.prefix").orNull
    ?.trim()?.takeIf { it.isNotEmpty() } ?: gropify.project.name
val apkOutputTail = providers.gradleProperty("apk.output.tail").orNull
    ?.trim()?.takeIf { it.isNotEmpty() }
val apkChannelSegment = providers.gradleProperty("apk.output.channel").orNull
    ?.trim()?.takeIf { it.isNotEmpty() }
val apkGeoSegment = "builtin"

// Mihomo kernel version resolution (configuration-cache compatible).
data class MihomoBuildInfo(
    val branch: String,
    val commit: String,
    val displayVersion: String,
    val gitVersionArg: String,
)

fun resolveMihomoBuildInfo(rootDir: File): MihomoBuildInfo {
    // Path 1: core-version.properties stamp (written by native-build.main.kts --go).
    // If the stamp has a valid commit, use its pre-computed values directly — no git or kernel.properties lookup needed.
    val stampFile = buildList {
        add(rootDir.resolve("build/generated/core-version.properties"))
        val jniRoot = rootDir.resolve("jniLibs")
        if (jniRoot.isDirectory) {
            jniRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }
                ?.forEach { add(File(it, "core-version.properties")) }
        }
    }.firstOrNull { f ->
        f.isFile && runCatching {
            Properties().apply { f.inputStream().use { s -> load(s) } }
        }.getOrNull()?.getProperty("core.commit")?.trim()?.let { c -> c.isNotEmpty() && c != "unknown" } == true
    }
    if (stampFile != null) {
        val p = Properties().apply { stampFile.inputStream().use { s -> load(s) } }
        return MihomoBuildInfo(
            branch = p.getProperty("core.branch", "Alpha"),
            commit = p.getProperty("core.commit", "unknown"),
            displayVersion = p.getProperty("core.displayVersion", ""),
            gitVersionArg = p.getProperty("core.gitVersion", ""),
        )
    }
    // Path 2: Fallback — compute from live git checkout + kernel.properties.
    val props = Properties()
    val kernelFile = rootDir.resolve("kernel.properties")
    if (kernelFile.isFile) kernelFile.inputStream().use { props.load(it) }
    val configuredBranch = props.getProperty("external.mihomo.branch", "Alpha").trim().ifEmpty { "Alpha" }
    val suffix = props.getProperty("external.mihomo.suffix", "").trim()
    val includeTimestamp = props.getProperty("external.mihomo.includeTimestamp", "false").toBooleanStrictOrNull() ?: false
    val mihomoDir = rootDir.resolve(props.getProperty("external.mihomo.dir", "lib/mihomo/mihomo").trim())
    val (gitCommit, gitBranch) = if (mihomoDir.isDirectory) {
        val hash = runCatching { providers.exec { commandLine("git", "-C", mihomoDir.absolutePath, "rev-parse", "--short=8", "HEAD"); workingDir = rootDir }.standardOutput.asText.get().trim() }.getOrDefault("").takeIf { it.isNotEmpty() && it.matches(Regex("[0-9a-fA-F]{4,40}")) }
        val branch = runCatching { providers.exec { commandLine("git", "-C", mihomoDir.absolutePath, "rev-parse", "--abbrev-ref", "HEAD"); workingDir = rootDir }.standardOutput.asText.get().trim() }.getOrDefault("").takeIf { it.isNotEmpty() && it != "HEAD" }
        (hash ?: "unknown") to branch
    } else "unknown" to null
    val commit = gitCommit
    val branchBase = configuredBranch.ifBlank { gitBranch ?: "mihomo" }
    val branchLabel = branchBase + suffix
    val timeStamp = if (includeTimestamp) SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date()) else "local"
    return MihomoBuildInfo(
        branch = branchLabel,
        commit = commit,
        displayVersion = "$branchLabel-$commit",
        gitVersionArg = "${branchLabel.replace('_', '-')}_${commit}_$timeStamp",
    )
}

val mihomoBuildInfo = resolveMihomoBuildInfo(rootProject.projectDir)

android {
    namespace = providers.gradleProperty("project.namespace.base").get()

    defaultConfig {
        applicationId = projectApplicationId
        targetSdk = providers.gradleProperty("android.targetSdk").get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
        manifestPlaceholders["appName"] = providers.gradleProperty("project.name").get()
        manifestPlaceholders["applicationClass"] = ".App"
        manifestPlaceholders["componentFactory"] = "androidx.core.app.CoreComponentFactory"
        buildConfigField("String", "UPDATE_REPOSITORY", updateRepository.asBuildConfigString())
        buildConfigField("String", "UPDATE_SOURCE", updateSource.asBuildConfigString())
        buildConfigField("String", "UI_BUILD_ID", updateUiBuildId.asBuildConfigString())
        buildConfigField("String", "UPDATE_MIRROR_TEMPLATES", updateMirrorTemplates.asBuildConfigString())
        // Mihomo core identity from kernel.properties / lib/mihomo checkout / core-version.stamp.
        buildConfigField("String", "KERNEL_GIT_VERSION", "\"${escapeBuildConfigString(mihomoBuildInfo.gitVersionArg)}\"")
        buildConfigField("String", "CORE_BRANCH", "\"${escapeBuildConfigString(mihomoBuildInfo.branch)}\"")
        buildConfigField("String", "CORE_COMMIT", "\"${escapeBuildConfigString(mihomoBuildInfo.commit)}\"")
        buildConfigField("String", "CORE_VERSION", "\"${escapeBuildConfigString(mihomoBuildInfo.displayVersion)}\"")
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
                "proguard/proguard-rules.keep",
            )
            if (releaseSigningProperties != null) {
                manifestPlaceholders["applicationClass"] = "dev.flycat.loader.LoaderApplication"
                manifestPlaceholders["componentFactory"] = "dev.flycat.loader.LoaderComponentFactory"
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
            include(*packagingAbiList.toTypedArray())
            isUniversalApk = buildAllAbis || withExtension
        }
    }

    packaging {
        jniLibs {
            // libjavet excluded from normal builds; WithExtension builds include it.
            if (!withExtension) { excludes += listOf("lib/**/libjavet*.so") }
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf("META-INF/**")
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
                val geoSegment = buildString {
                    append(apkGeoSegment)
                    if (withExtension) append("_Extension")
                }
                val outputName = buildList {
                    add(apkOutputPrefix)
                    add(geoSegment)
                    add(abiName)
                    apkChannelSegment?.let(::add)
                    add("release")
                    add(updateUiBuildId)
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
                outputDirectory.set(layout.buildDirectory.dir("intermediates/flycatPacker/${variant.name}/loaderDex"))
                dependsOn(":pack:bundleReleaseAar")
            }
            val packApkTask = tasks.register<TransformPackedApksTask>("pack${capitalized}Apk") {
                group = "build"
                description = "Compresses DEX payloads and installs the loader in ${variant.name} APKs"
                loaderDex.set(loaderDexTask.flatMap { it.outputDirectory.file("classes.dex") })
                sdkDirectory.set(sdkComponents.sdkDirectory)
                originalApplication.set("com.github.lmfirefly.flycat.App")
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":core"))
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
    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)

    //noinspection NewerVersionAvailable
    implementation("com.tencent:mmkv:${rootProject.extra["mmkvVersion"]}")

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.timber)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
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
