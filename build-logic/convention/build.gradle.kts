plugins {
    `kotlin-dsl`
}

group = "com.zaus.nullwave.buildlogic"

// The precompiled script plugins in src/main/kotlin use `plugins { id("...") }`, which resolves
// against this project's compile classpath rather than the plugin portal - hence these.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.metro.gradlePlugin)
    compileOnly(libs.serialization.gradlePlugin)
}
