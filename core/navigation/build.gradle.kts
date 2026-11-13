plugins {
    id("nullwave.android.library")
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    testImplementation(libs.junit)
}

android {
    namespace = "com.zaus.nullwave.core.navigation"
    buildFeatures.compose = true
}
