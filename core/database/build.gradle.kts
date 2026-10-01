plugins {
    id("nullwave.android.library")
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.zaus.nullwave.core.database"
}

// This module exists so that the generated types can be reached by :core:data and by nothing else.
// :core:data depends on it with `implementation`, which Gradle does not propagate to consumers, so a
// feature module cannot name `com.zaus.nullwave.data.database.Track` at all - it is not on its compile
// classpath. The domain model in :core:data stops being a convention and becomes the only option.
sqldelight {
    databases {
        create("NullWaveDatabase") {
            // .sq files go in core/database/src/main/sqldelight/com/zaus/nullwave/data/database/
            //
            // The package deliberately does not track the module path. That is what made this split
            // cheap: moving the schema between modules moved no imports in :core:data.
            packageName.set("com.zaus.nullwave.data.database")
            // `./gradlew :core:database:generateNullWaveDatabaseSchema` writes a .db snapshot here; the
            // verify task then checks each .sqm migration against it. Do not run it until the schema has
            // settled - from that point on every change needs a numbered migration.
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

dependencies {
    // `api`, not `implementation`: DatabaseBindings provides SqlDriver and NullWaveDatabase into the
    // Metro graph, and the graph code generated in :app has to be able to name both return types.
    // android-driver brings the sqldelight runtime (SqlDriver, Query, Transacter) with it.
    api(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.primitive.adapters)

    // DatabaseBindings names SupportSQLiteDatabase for the driver's onOpen callback. It does arrive
    // transitively with the driver, but relying on that means this module breaks silently the day the
    // driver reorganises its dependencies. Declared.
    implementation(libs.androidx.sqlite)

    // A real schema in a JVM test, no device needed - the fastest way to find out a .sq file is wrong.
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.junit)
}
