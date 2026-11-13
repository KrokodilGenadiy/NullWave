plugins {
    id("nullwave.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zaus.nullwave.feature.equalizer.api"
}

dependencies {
    api(project(":core:navigation"))
    implementation(libs.kotlinx.serialization.core)
}
