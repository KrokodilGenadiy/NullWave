// A feature's PUBLIC CONTRACT: its NavKeys, plus the interfaces other features compile against.
//
// Deliberately lean - no Compose, no design system, no :core:navigation. An api module should stay
// cheap to depend on, because every feature that links to this one will. If something here wants
// Compose, it belongs in the impl module instead.
//
// Navigation 3 and kotlinx-serialization are here rather than in each module's build file: every
// feature declares @Serializable NavKeys, so it is the same three lines every time.
plugins {
    id("nullwave.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    // api: a NavKey type appears in the signatures other modules use.
    "api"(libs.findLibrary("androidx-navigation3-runtime").get())
    // Contracts expose Flow, so coroutines is part of the surface too.
    "api"(libs.findLibrary("kotlinx-coroutines-android").get())
    "implementation"(libs.findLibrary("kotlinx-serialization-core").get())
}
