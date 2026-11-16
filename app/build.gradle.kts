plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.tvhome"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.tvhome.app.vtest"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "vtest-1.0"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("tvhome.jks")
            storePassword = "tvhome123"
            keyAlias = "tvhome"
            keyPassword = "tvhome123"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
