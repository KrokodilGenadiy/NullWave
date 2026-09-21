plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.core.navigation"
}

dependencies {
    // EntryProviderInstaller is written in terms of EntryProviderScope / NavKey.
    api(libs.androidx.navigation3.runtime)
}

// Deliberately holds no NavKeys. Each feature declares its own in its :api module, so adding a
// feature never touches this module.
