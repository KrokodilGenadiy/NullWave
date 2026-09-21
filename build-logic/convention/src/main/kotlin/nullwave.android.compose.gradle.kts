// An Android library that draws UI. Adds the Compose compiler plugin and the Compose deps every
// UI module needs, so modules do not repeat the BOM dance.
plugins {
    id("nullwave.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Precompiled script plugins cannot use the `libs.` accessors directly; look the catalog up.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    val bom = platform(libs.findLibrary("androidx-compose-bom").get())
    "api"(bom)
    "androidTestImplementation"(bom)

    "api"(libs.findLibrary("androidx-compose-ui").get())
    "api"(libs.findLibrary("androidx-compose-ui-graphics").get())
    "api"(libs.findLibrary("androidx-compose-material3").get())
    "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
    "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
}
