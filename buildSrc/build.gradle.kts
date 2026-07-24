plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    //noinspection AndroidLintUseTomlInstead
    implementation("org.tukaani:xz:1.12")
}
