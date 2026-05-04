// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")              // KSP for Room annotation processing
}

android {
    namespace = "com.triggerchain"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.triggerchain"
        minSdk = 26                            // UsageStatsManager API 26+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Room schema export path
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    val roomVersion = "2.6.1"
    val workVersion = "2.9.0"
    val coroutinesVersion = "1.7.3"
    val lifecycleVersion = "2.7.0"

    // ── Room ──────────────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ── Coroutines + Flow ─────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // ── WorkManager ───────────────────────────────────────────────────────
    implementation("androidx.work:work-runtime-ktx:$workVersion")

    // ── Lifecycle (ViewModel / StateFlow integration) ─────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")

    // ── JSON serialisation (Gson — on-device only) ────────────────────────
    implementation("com.google.code.gson:gson:2.10.1")

    // ── Core Android ──────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.work:work-testing:$workVersion")
}
