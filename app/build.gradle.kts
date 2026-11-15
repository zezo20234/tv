plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.realm.tvos"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.realm.tvos.app"
        minSdk = 21
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 21) + 20
        versionName = "21.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("realm_tvos.jks")
            storePassword = "realm123"
            keyAlias = "realm"
            keyPassword = "realm123"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
