plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

android {
    namespace = "com.zaus.nullwave"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.zaus.nullwave"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// The sqldelight { } block and its source set now live in :core:data. They were here, which would have
// generated NullWaveDatabase into :app — invisible to every feature module, since dependencies run
// :app -> :feature -> :core. See DATA.md.

dependencies {
    // Every feature module must be on :app's COMPILE classpath, not runtimeOnly - Metro resolves
    // contribution hints in FIR, which only sees the compile classpath. This is the one place a
    // feature is named; nothing in :app's source imports from them.
    // Both data modules, because Metro resolves contribution hints against the COMPILE classpath and
    // each contributes a binding container: DatabaseBindings from :core:database, RepositoryBindings
    // from :core:data. :app is the one module allowed to see the schema; features see only :core:data.
    implementation(projects.core.database)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    // Contributes PreferencesBindings. Named here for the same reason as the two above: Metro resolves
    // contribution hints against the compile classpath.
    implementation(projects.core.preferences)
    implementation(projects.core.di)
    implementation(projects.core.navigation)
    // impl modules must be on the COMPILE classpath (Metro resolves contribution hints in FIR),
    // so runtimeOnly will not work. This is the only place an impl is named; :app imports from
    // api modules only.
    implementation(projects.feature.library.impl)
    implementation(projects.feature.player.impl)
    implementation(projects.feature.search.impl)
    implementation(projects.feature.equalizer.impl)
    implementation(projects.feature.sleeptimer.impl)
    implementation(projects.feature.settings.impl)
    implementation(projects.feature.about.impl)

    // :app names the start destination, so it needs the library feature's contract. The drawer
    // will add the other api modules as it grows - api only, never impl.
    implementation(projects.feature.library.api)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    // Uncomment together with the ADAPTIVE LAYOUT block at the foot of MainActivity.kt.
    // implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.kotlinx.coroutines.android)

    // Metro contributes its own runtime via the compiler plugin; no explicit dependency needed.
    // MetroX Android is a separate library, though. Its AAR manifest merges
    // android:appComponentFactory in for you, so AndroidManifest.xml needs no edit.
    implementation(libs.metrox.android)

    // AppGraph implements ViewModelGraph, and NullWaveApp provides the factory and the entry decorator.
    // :app is the only module that wires them; features just contribute @ViewModelKey bindings.
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // The sqldelight artifacts moved to :core:data with the schema. :app still gets the Android driver
    // transitively (it is `api` there), because the DatabaseBindings that construct AndroidSqliteDriver
    // have to live somewhere the Metro graph can see.

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

}
