plugins {
    id("nullwave.android.feature")
}

android {
    namespace = "com.zaus.nullwave.feature.about"
}

dependencies {
    api(projects.feature.about.api)
}