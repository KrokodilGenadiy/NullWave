pluginManagement {
    // The convention plugins (nullwave.android.library / .compose / .feature / .feature.api)
    // live here.
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Lets modules write `implementation(projects.core.designsystem)` instead of a string path.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "NullWave"

include(":app")

// The schema and the repositories are separate modules on purpose. :core:data depends on
// :core:database with `implementation`, which keeps the generated SQLDelight types off every feature's
// compile classpath - so a feature cannot reach past the domain model even by accident. See DATA.md.
include(":core:database")
include(":core:data")
include(":core:designsystem")
// The DataStore mechanism only. Typed accessors live with whoever owns the values - SettingsRepository
// in :feature:settings, the permission flag in :core:data - so this module never becomes a bag of
// unrelated keys. See LIBRARY.md §1a.
include(":core:preferences")
include(":core:di")
include(":core:navigation")

// Every feature is api + impl. api holds the NavKeys and the contracts other features compile
// against; impl holds screens, bindings and everything else. A feature depends on other features'
// api, never their impl.
include(":feature:library:api")
include(":feature:library:impl")
include(":feature:player:api")
include(":feature:player:impl")
include(":feature:search:api")
include(":feature:search:impl")
include(":feature:equalizer:api")
include(":feature:equalizer:impl")
include(":feature:sleeptimer:api")
include(":feature:sleeptimer:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
include(":feature:about:api")
include(":feature:about:impl")
