plugins {
    id("nullwave.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zaus.nullwave.feature.player.api"
    buildFeatures.compose = true
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(project(":core:mvi"))
    api(project(":core:playback:api"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:mvi")))
}
