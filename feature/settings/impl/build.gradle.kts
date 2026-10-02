plugins {
    id("nullwave.android.feature")
}

android {
    namespace = "com.zaus.nullwave.feature.settings"
}

dependencies {
    api(projects.feature.settings.api)

    // The store comes from :core:preferences, which owns the single DataStore instance - a second one
    // over the same file in the same process throws, so this module must not create its own. Depending
    // on the artifact directly would compile and then fail at runtime.
    implementation(projects.core.preferences)

    testImplementation(libs.junit)
}
