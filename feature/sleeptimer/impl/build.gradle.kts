plugins {
    id("nullwave.android.feature")
}

android {
    namespace = "com.zaus.nullwave.feature.sleeptimer"
}

dependencies {
    api(projects.feature.sleeptimer.api)
}