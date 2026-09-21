plugins {
    id("nullwave.android.feature")
}

android {
    namespace = "com.zaus.nullwave.feature.player"
}

dependencies {
    api(projects.feature.player.api)
}
