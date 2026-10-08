plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.feature.lyrics.api"
}

dependencies {
    implementation(project(":core:navigation"))
}
