plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.core.playback.api"
}

dependencies {
    api(project(":core:media"))
    api(project(":core:result"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
