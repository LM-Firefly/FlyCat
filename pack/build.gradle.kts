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
        }
    }
}

dependencies {
    implementation(libs.hiddenapibypass)
}
