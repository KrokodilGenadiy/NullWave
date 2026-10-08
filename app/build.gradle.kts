plugins {
    id("nullwave.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

android {
    namespace = "com.zaus.nullwave"

    defaultConfig {
        applicationId = "com.zaus.nullwave"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":feature:library:api"))
    implementation(libs.androidx.navigation3.ui)
    implementation(project(":core:di"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:library:impl"))
    implementation(project(":feature:playlists:impl"))
    implementation(project(":feature:search:impl"))
    implementation(project(":feature:player:impl"))
    implementation(project(":feature:lyrics:impl"))
    implementation(project(":feature:equalizer:impl"))
    implementation(project(":feature:sleeptimer:impl"))
    implementation(project(":feature:settings:impl"))
    implementation(project(":feature:about:impl"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
