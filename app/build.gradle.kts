plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.educafit.ai"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.educafit.ai"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures { compose = true }

    androidResources {
        // GGUF is already quantized. Avoid wasting time recompressing a large model.
        noCompress += "gguf"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":llama-kt"))

    implementation(platform("androidx.compose:compose-bom:2025.03.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
