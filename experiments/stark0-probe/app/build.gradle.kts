plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.shaikh.stark0"
    compileSdk = 34
    defaultConfig {
        applicationId = "dev.shaikh.stark0"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-probe"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
