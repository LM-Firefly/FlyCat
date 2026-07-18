plugins {
    id("com.android.library")
}

android {
    sourceSets {
        getByName("main") {
            kotlin.directories.apply {
                clear()
                add("src")
            }
        }
    }
}
