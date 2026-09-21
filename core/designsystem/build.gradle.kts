plugins {
    id("nullwave.android.compose")
}

android {
    namespace = "com.zaus.nullwave.core.designsystem"
}

// No dependencies of its own: the design system is tokens, icons and fonts. NavKeys moved to
// :core:navigation, which took the Navigation 3 and serialization dependencies with them.
