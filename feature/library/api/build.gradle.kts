plugins {
    id("nullwave.android.feature.api")
}

android {
    namespace = "com.zaus.nullwave.feature.library.api"
}

// Navigation 3 and serialization come from the nullwave.android.feature.api convention plugin -
// every api module declares NavKeys, so it is not repeated per module.
