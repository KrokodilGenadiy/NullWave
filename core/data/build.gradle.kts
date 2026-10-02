plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.core.data"
}

dependencies {
    // `implementation`, and that is the whole point of the split. Gradle does not propagate an
    // `implementation` dependency to consumers, so :core:database's generated types are on THIS module's
    // compile classpath and on no feature's. A feature gets the domain model in model/ or nothing -
    // `import com.zaus.nullwave.data.database.Track` does not resolve for it.
    //
    // Switching this to `api` would silently undo that.
    implementation(projects.core.database)

    // For AudioPermissionState's flag. `api` so a consumer of this module can observe the Flow without
    // separately declaring DataStore - the flag's type is Boolean, but the module that provides the store
    // is a transitive the repository's callers should not have to know about.
    api(projects.core.preferences)

    // `.asFlow()` / `.mapToList()` on a generated query, which is what makes repository reads re-emit
    // after a rescan. The sqldelight runtime itself arrives transitively, via :core:database's `api` on
    // the android driver.
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.kotlinx.coroutines.android)

    // No coroutines-test: every test here is synchronous. Add it back when something suspends.
    testImplementation(libs.junit)
}
