// ─── Dev 2 additions to app/build.gradle.kts ─────────────────────────────────
// Merge these into Dev 1's existing build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    compileSdk = 34

    defaultConfig {
        applicationId  = "com.triggerchain"
        minSdk         = 26
        targetSdk      = 34
        versionCode    = 1
        versionName    = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // ── Dev 1 deps (keep as-is) ────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    ksp("androidx.room:room-compiler:2.6.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ── Dev 2: Compose BOM ─────────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Dev 2: Navigation ──────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // ── Dev 2: Lifecycle ───────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // ── Dev 2: Activity for Compose ────────────────────────────────────────
    implementation("androidx.activity:activity-compose:1.8.2")

    // ── Dev 2: Notifications ───────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.12.0")
}
