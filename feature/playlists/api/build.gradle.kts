plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.feature.playlists.api"
}

dependencies {
    implementation(project(":core:navigation"))
}
