plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.core.di"
}

dependencies {
    // api: features annotate with ActivityKey and contribute Activity bindings, so they need
    // MetroX's types on their own compile classpath.
    api(libs.metrox.android)
}
