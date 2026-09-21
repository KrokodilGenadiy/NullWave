plugins {
    id("nullwave.android.feature")
}

android {
    namespace = "com.zaus.nullwave.feature.library"
}

dependencies {
    api(projects.feature.library.api)

    // Cross-feature dependencies are always on the OTHER feature's api, never its impl.
    // This is the line that keeps a change to the player's internals from recompiling the library.
    implementation(projects.feature.player.api)
}
