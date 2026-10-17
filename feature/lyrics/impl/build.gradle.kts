plugins {
    id("nullwave.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zaus.nullwave.feature.lyrics.impl"
    buildFeatures.compose = true
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(project(":feature:lyrics:api"))
    implementation(project(":core:di"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:mvi"))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
