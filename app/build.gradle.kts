plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.customkey.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.customkey.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
}
