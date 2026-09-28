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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}
