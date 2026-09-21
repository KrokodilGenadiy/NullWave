plugins {
    id("nullwave.android.feature")
}

android {
    namespace = "com.zaus.nullwave.feature.search"
}

dependencies {
    api(projects.feature.search.api)
}