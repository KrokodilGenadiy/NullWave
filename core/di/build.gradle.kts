plugins {
    id("nullwave.android.library")
    alias(libs.plugins.metro)
}

android {
    namespace = "com.zaus.nullwave.core.di"
}

dependencies {
    api(libs.metrox.viewmodel)
    api(libs.metrox.viewmodel.compose)
    api(libs.androidx.lifecycle.viewmodel.savedstate)
    testImplementation(project(":core:mvi"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
