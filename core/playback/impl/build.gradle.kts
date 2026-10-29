plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.core.playback.impl"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
    implementation(project(":core:playback:api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.media3.test.utils)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
