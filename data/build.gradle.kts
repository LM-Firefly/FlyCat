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

plugins {
    id("flycat-android-library")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room3")
}

android {
    namespace = "com.github.lmfirefly.flycat.data"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":locale"))
    implementation(project(":runtime:api"))

    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.snakeyaml.engine)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.timber)
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)

    implementation("com.tencent:mmkv:${rootProject.extra["mmkvVersion"]}")

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
