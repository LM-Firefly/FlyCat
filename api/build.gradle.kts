/*
 * This file is part of YumeBox.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

group = providers.gradleProperty("api.group").orElse("io.github.yumeyucca.yumebox").get()
version = providers.gradleProperty("api.version").orElse("0.1.0-SNAPSHOT").get()

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "api"

            pom {
                name.set("YumeBox API")
                description.set("Kotlin client for the YumeBox controller REST API.")
                url.set("https://github.com/YumeYucca/YumeBox")
                licenses {
                    license {
                        name.set("GNU Affero General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.html")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/YumeYucca/YumeBox.git")
                    developerConnection.set("scm:git:ssh://git@github.com/YumeYucca/YumeBox.git")
                    url.set("https://github.com/YumeYucca/YumeBox")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            val repository =
                providers
                    .gradleProperty("api.githubRepository")
                    .orElse("YumeYucca/YumeBox")
                    .get()
            url = uri("https://maven.pkg.github.com/$repository")
            credentials {
                username =
                    providers
                        .gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                        .orNull
                password =
                    providers
                        .gradleProperty("gpr.key")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                        .orNull
            }
        }
    }
}
