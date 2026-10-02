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

    // The library is the first consumer of the data layer: Songs / Artists / Albums all read from it.
    implementation(projects.core.data)

    // For `rememberLauncherForActivityResult` in AudioAccessGate. Only in :app until now - features that
    // request a runtime permission need it too, and the library is the only one that does.
    implementation(libs.androidx.activity.compose)
}
