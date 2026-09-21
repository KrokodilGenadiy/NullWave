// build-logic is a separate, included build. It compiles the convention plugins that every
// module applies, so module build files stay three lines long.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        // Share the main build's catalog so versions are declared in exactly one place.
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "build-logic"
include(":convention")
