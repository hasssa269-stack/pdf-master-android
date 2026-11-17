plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pdfmaster.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pdfmaster.ai"
        minSdk = 26
        targetSdk = 35

        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
    }
}

dependencies {

    // Android Core
    implementation("androidx.core:core-ktx:1.15.0")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose
    implementation(
        platform("androidx.compose:compose-bom:2024.10.00")
    )

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // PDF Processing
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // XML parsing for Word documents
    implementation("androidx.core:core-ktx:1.15.0")

    // Debug tools
    debugImplementation("androidx.compose.ui:ui-tooling")
}
