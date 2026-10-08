plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.feature.library.api"
}

dependencies {
    implementation(project(":core:navigation"))
}
