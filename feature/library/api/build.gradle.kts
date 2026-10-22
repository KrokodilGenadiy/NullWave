plugins {
    id("nullwave.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zaus.nullwave.feature.library.api"
    buildFeatures.compose = true
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(project(":core:navigation"))
    api(project(":core:result"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.junit)
}
