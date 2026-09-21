plugins {
    id("nullwave.android.feature")
}

android {
    namespace = "com.zaus.nullwave.feature.settings"
}

dependencies {
    api(projects.feature.settings.api)

    // The settings store itself. Only this module touches DataStore; everyone else goes through
    // SettingsRepository from :feature:settings:api.
    implementation(libs.androidx.datastore.preferences)
}
