plugins {
    id("nullwave.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zaus.nullwave.feature.library.api"
}

dependencies {
    api(project(":core:navigation"))
    api(project(":core:result"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.junit)
}
