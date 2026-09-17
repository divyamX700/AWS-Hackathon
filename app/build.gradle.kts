plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.sankatsetu.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sankatsetu.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-hackathon"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // flip on once we've verified Cedar/MediaPipe JNI survive R8
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            // Demo-mode flag: disables the panic-wipe triple-tap so judges
            // fumbling the demo phone don't nuke it (see docs/adr/0004).
            buildConfigField("boolean", "DEMO_MODE_DEFAULT", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // --- Persistence: Room + SQLCipher (matches Flowpay's data-layer pattern) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // --- Crypto: Noise Protocol (XX/X patterns) + Ed25519 signing ---
    // rweather/noise-java — reference Java implementation of the Noise Protocol
    // Framework. Ported/wrapped in mesh/crypto/NoiseSession.kt. If this exact
    // artifact isn't resolvable from Maven Central at build time, vendor the
    // ~15 source files from https://github.com/rweather/noise-java directly
    // into app/src/main/java/com/southernstorm/noise/ (public domain / MIT-ish,
    // see NOTICE.md) — see docs/adr/0002-vendoring-strategy.md.
    implementation("com.southernstorm:noise-java:0.1.0")

    // --- QR (setup handshake, officer key exchange) ---
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
