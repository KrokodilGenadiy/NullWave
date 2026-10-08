plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.feature.sleeptimer.api"
}

dependencies {
    implementation(project(":core:navigation"))
}
