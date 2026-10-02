plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.core.preferences"
}

dependencies {
    // `api`, deliberately - unlike :core:database, which hides its generated types behind
    // `implementation`.
    //
    // The difference is what there is to hide. SQLDelight's types exist only in that module, so keeping
    // them off a feature's classpath genuinely prevents something. `DataStore<Preferences>` is an
    // androidx type any module could depend on directly, so hiding it would buy a rule rather than a
    // guarantee - and the consumers (SettingsRepository, the permission flag) need to name it.
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
