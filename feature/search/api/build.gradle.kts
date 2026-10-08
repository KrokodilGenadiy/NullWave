plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.feature.search.api"
}

dependencies {
    implementation(project(":core:navigation"))
}
