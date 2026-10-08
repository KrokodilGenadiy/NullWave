plugins {
    id("nullwave.android.library")
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    testImplementation(libs.junit)
}

android {
    namespace = "com.zaus.nullwave.core.navigation"
}
