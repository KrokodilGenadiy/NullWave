plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.feature.equalizer.api"
}

dependencies {
    implementation(project(":core:navigation"))
}
