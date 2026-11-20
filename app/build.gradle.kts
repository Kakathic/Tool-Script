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
        // CI truyền -PversionCode (số lần chạy workflow); -PversionName tùy chọn. Build local dùng giá trị mặc định.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse("0.1.0")
    }

    // Khóa "beta": keystore cố định commit trong repo (app/beta.jks), mật khẩu công khai nên KHÔNG bảo mật.
    // Dùng tạm cho cả bản debug lẫn bản chính thức (release): mọi build cùng chữ ký nên cài đè / cập nhật lên nhau được.
    // Khi có khóa thật, thay storeFile / mật khẩu / alias của signingConfig này (hoặc thêm signingConfig mới cho release).
    // Lưu ý: đổi chữ ký thì người dùng phải gỡ app cũ rồi cài lại.
    signingConfigs {
        create("beta") {
            storeFile = file("beta.jks")
            storePassword = "android"
            keyAlias = "beta"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("beta")
        }
        release {
            signingConfig = signingConfigs.getByName("beta")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Lệnh dựng sẵn (jniLibs/<abi>/libbin_*.so) phải được giải nén ra nativeLibraryDir để chạy được; không strip.
            useLegacyPackaging = true
            keepDebugSymbols += "**/lib_*.so"
        }
    }
}

// Tắt các dòng "w: ..." (cảnh báo trình biên dịch Kotlin) khi build. Lỗi (e:) vẫn hiện.
kotlin {
    compilerOptions {
        suppressWarnings.set(true)
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
