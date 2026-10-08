plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.feature.settings.api"
}

dependencies {
    implementation(project(":core:navigation"))
}
