plugins {
    id("nullwave.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

android {
    namespace = "com.zaus.nullwave.feature.equalizer.impl"
    buildFeatures.compose = true
}

dependencies {
    implementation(project(":feature:equalizer:api"))
    implementation(project(":core:di"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:mvi"))
}
