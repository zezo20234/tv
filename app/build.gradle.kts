plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.tvhome"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.tvhome.app"
        minSdk = 21
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 10) + 10
        versionName = "10.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
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
