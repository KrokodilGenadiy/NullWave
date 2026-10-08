// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Keep AGP and the Compose plugin visible in the same parent classloader.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
