pluginManagement {
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

rootProject.name = "NullWave"
include(":app")

include(
    ":core:di",
    ":core:navigation",
    ":core:designsystem",
    ":core:mvi",
    ":feature:library:api",
    ":feature:library:impl",
    ":feature:playlists:api",
    ":feature:playlists:impl",
    ":feature:search:api",
    ":feature:search:impl",
    ":feature:player:api",
    ":feature:player:impl",
    ":feature:lyrics:api",
    ":feature:lyrics:impl",
    ":feature:equalizer:api",
    ":feature:equalizer:impl",
    ":feature:sleeptimer:api",
    ":feature:sleeptimer:impl",
    ":feature:settings:api",
    ":feature:settings:impl",
    ":feature:about:api",
    ":feature:about:impl",
)
