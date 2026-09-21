plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
    alias(libs.plugins.sqldelight)
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

sqldelight {
    databases {
        create("NullWaveDatabase") {
            // .sq files go in app/src/main/sqldelight/com/zaus/nullwave/data/database/
            // The NullWaveDatabase class is only generated once at least one .sq file exists.
            packageName.set("com.zaus.nullwave.data.database")
            // `./gradlew :app:generateNullWaveDatabaseSchema` writes a .db snapshot here; the
            // verify task then checks each .sqm migration against it.
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

dependencies {
    // Every feature module must be on :app's COMPILE classpath, not runtimeOnly - Metro resolves
    // contribution hints in FIR, which only sees the compile classpath. This is the one place a
    // feature is named; nothing in :app's source imports from them.
    implementation(projects.core.designsystem)
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
    implementation(libs.kotlinx.coroutines.android)

    // Metro contributes its own runtime via the compiler plugin; no explicit dependency needed.
    // MetroX Android is a separate library, though. Its AAR manifest merges
    // android:appComponentFactory in for you, so AndroidManifest.xml needs no edit.
    implementation(libs.metrox.android)

    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.sqldelight.primitive.adapters)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqldelight.sqlite.driver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

}
