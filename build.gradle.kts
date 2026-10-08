// Override AGP's built-in Kotlin version to match the Compose compiler and Metro.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    // Keep AGP and the Compose plugin visible in the same parent classloader.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.metro) apply false
}
