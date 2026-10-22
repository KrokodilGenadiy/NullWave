plugins {
    id("nullwave.android.library")
    alias(libs.plugins.metro)
}

android {
    namespace = "com.zaus.nullwave.feature.search.impl"
}

dependencies {
    implementation(project(":feature:search:api"))
    implementation(project(":feature:library:api"))
    implementation(project(":core:di"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:mvi"))
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
