plugins {
    id("nullwave.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zaus.nullwave.core.mvi"
    buildFeatures.compose = true
}

dependencies {
    api(project(":core:result"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.lifecycle.runtime)
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
}
