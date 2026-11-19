plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tool.script"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tool.script"
        minSdk = 30            // Wireless Debugging (backend adb) và MANAGE_EXTERNAL_STORAGE cần Android 11+
        targetSdk = 37         // Android 17 (API 37)
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose + Miuix
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)

    // TOML
    implementation(libs.tomlkt)
    implementation(libs.kotlinx.serialization.core)

    // Backend su
    implementation(libs.libsu.core)

    // Icon tab từ đường dẫn
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
}
