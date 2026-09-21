// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.metro) apply false
    // NOTE: SQLDelight is deliberately NOT declared here, not even with `apply false`.
    // Its Gradle plugin drags kotlin-stdlib onto the ROOT buildscript classpath, which makes
    // Gradle pin `org.jetbrains:annotations` to the embedded Kotlin's 13.0 while AGP 9.3.3
    // needs 23.0.0 — an unresolvable conflict. Applying it straight in :app avoids the root
    // classpath entirely; the version still comes from libs.versions.toml.
}