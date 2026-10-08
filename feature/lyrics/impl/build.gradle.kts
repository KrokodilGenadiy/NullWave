plugins {
    id("nullwave.android.library")
}

android {
    namespace = "com.zaus.nullwave.feature.lyrics.impl"
}

dependencies {
    implementation(project(":feature:lyrics:api"))
    implementation(project(":core:di"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:mvi"))
}
