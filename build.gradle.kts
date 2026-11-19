// AGP 9 có sẵn Kotlin tích hợp (built-in Kotlin) nên KHÔNG khai báo plugin kotlin.android.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
