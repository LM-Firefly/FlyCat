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
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Sync

plugins {
    id("com.android.application")
}

dependencies {
    implementation(libs.javet.node.android)
}

android {
    namespace = gropify.project.namespace.extension

    defaultConfig {
        applicationId = gropify.project.namespace.extension
        minSdk = gropify.android.minSdk
        targetSdk = gropify.android.targetSdk
        versionCode = gropify.project.version.code
        versionName = gropify.project.version.name
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            val abiList =
                (gropify.abi.extension.list ?: "arm64-v8a,x86_64")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            // Qualify receiver so Kotlin does not resolve include() against Iterable from the
            // chain.
            @Suppress("SpreadOperator") this@abi.include(*abiList.toTypedArray())
            isUniversalApk = false
        }
    }
}

tasks.register<Sync>("collectJavetNative") {
    group = "distribution"
    description = "Extracts the arm64-v8a Javet native library for the Expand release"
    from(provider { configurations.getByName("releaseRuntimeClasspath").files.map(::zipTree) }) {
        include("jni/arm64-v8a/libjavet-node-android.v.5.0.9.so")
        rename { "libjavet.so" }
        eachFile { relativePath = RelativePath(true, name) }
        includeEmptyDirs = false
    }
    into(rootProject.layout.projectDirectory.dir("output_apk/Expand"))
}
