plugins {
    id("flycat-android-library")
    kotlin("plugin.compose")
}

android {
    buildFeatures {
        compose = true
    }
}
