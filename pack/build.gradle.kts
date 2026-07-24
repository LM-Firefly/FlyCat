plugins {
    id("com.android.library")
}

android {
    namespace = "dev.yume.loader"

    sourceSets.named("main") {
        java.setSrcDirs(listOf("src"))
        manifest.srcFile("AndroidManifest.xml")
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(libs.hiddenapibypass)
    implementation(libs.androidx.annotation.jvm)
}
