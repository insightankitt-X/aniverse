plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "eu.kanade.tachiyomi.animesource"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=keiyoushi.annotation.AnikkuOnly",
            "-opt-in=keiyoushi.annotation.AnimiruOnly",
        )
    }
}

dependencies {
    // These are `compileOnly` for extensions, so the host has to ship them at runtime.
    api(libs.okhttp)
    api(libs.jsoup)
    api(libs.nanohttpd)
    api(libs.androidx.preference)
    api(libs.injekt.core)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.quickjs)
}
