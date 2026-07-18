plugins {
    id("flycat-android-library")
}

android {
    namespace = "dev.flycat.loader"
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
