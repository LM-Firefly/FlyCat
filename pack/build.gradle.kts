plugins {
    id("com.android.library")
}

android {
    namespace = "dev.yume.loader"

    sourceSets {
        getByName("main") {
            java.directories.apply {
                clear()
                add("src")
            }
            manifest.srcFile("AndroidManifest.xml")
        }
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(libs.hiddenapibypass)
}
