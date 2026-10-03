// A feature module: Compose UI plus the three things every feature needs - the design system,
// the shared DI scopes, and the navigation contracts (NavKeys, EntryProviderInstaller).
plugins {
    id("nullwave.android.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "api"(project(":core:designsystem"))
    "api"(project(":core:di"))
    "api"(project(":core:navigation"))

    "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
    "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    "implementation"(libs.findLibrary("kotlinx-coroutines-android").get())

    // Every feature declares its ViewModels the same way, so the annotations and the `metroViewModel()`
    // accessor belong in the convention rather than being re-added per module.
    "api"(libs.findLibrary("metrox-viewmodel").get())
    "implementation"(libs.findLibrary("metrox-viewmodel-compose").get())
}
