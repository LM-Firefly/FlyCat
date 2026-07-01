plugins {
    id("com.android.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.github.yumelira.yumebox.feature.update"

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":locale"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.timber)
    implementation(libs.androidx.core.ktx)
}
