// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.android.library) apply false
    // Needed here even though only build-logic's convention plugins apply it: a precompiled script
    // plugin resolves `id(...)` against the applying build's plugin classpath, which this populates.
    alias(libs.plugins.kotlin.serialization) apply false
}
