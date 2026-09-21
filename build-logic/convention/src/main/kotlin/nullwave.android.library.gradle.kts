// Base for every Android library module. Applies Metro too, since any module may declare
// @Inject / @ContributesBinding.
//
// Each module still sets its own `android { namespace = "..." }`.
plugins {
    id("com.android.library")
    id("dev.zacsweers.metro")
}

android {
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
