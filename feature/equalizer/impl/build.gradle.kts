plugins {
    id("nullwave.android.feature")
}

android {
    namespace = "com.zaus.nullwave.feature.equalizer"
}

dependencies {
    api(projects.feature.equalizer.api)
}