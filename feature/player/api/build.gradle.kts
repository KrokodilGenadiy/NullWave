plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.feature.player.api"
}

dependencies {
    implementation(project(":core:navigation"))
}
