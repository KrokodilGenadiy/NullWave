plugins {
    id("nullwave.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zaus.nullwave.core.designsystem"
    buildFeatures.compose = true
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
