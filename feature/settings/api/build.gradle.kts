plugins {
    id("nullwave.android.feature.api")
}

android {
    namespace = "com.zaus.nullwave.feature.settings.api"
}

dependencies {
    // UserSettings carries the theme variant, which is a design system type.
    api(projects.core.designsystem)
}
