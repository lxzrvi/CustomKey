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
        versionCode = 3
        versionName = "1.2"
    }

    val releaseKeystore = rootProject.file("release.keystore")

    signingConfigs {
        create("release") {
            // The repo ships a demo keystore so CI can produce an installable
            // signed APK out of the box. Replace it (and/or set the
            // CK_* environment variables) with your own key for store uploads.
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = System.getenv("CK_STORE_PASSWORD") ?: "customkey"
                keyAlias = System.getenv("CK_KEY_ALIAS") ?: "customkey"
                keyPassword = System.getenv("CK_KEY_PASSWORD") ?: "customkey"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(17)
}
