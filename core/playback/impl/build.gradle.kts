plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.core.playback.impl"
}

dependencies {
    implementation(project(":core:playback:api"))
}
