plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.sankatsetu.app"
    // compileSdk/targetSdk 34, not 35: build-tools 35.0.0's aapt2 fails to
    // even parse platform 35's android.jar under AGP 7.4.2
    // ("RES_TABLE_TYPE_TYPE entry offsets overlap actual entry data") — a
    // real aapt2/AGP-era incompatibility, not just a version-support flag.
    // 34 is well inside AGP 7.4.2's tested range. See docs/adr/0006.
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.sankatsetu.app"
        minSdk = 29
        targetSdk = 34
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

    // Kotlin 1.9.24's Compose compiler is a separate artifact selected here
    // (pre-K2 model) rather than via the org.jetbrains.kotlin.plugin.compose
    // Gradle plugin (Kotlin 2.0+ only) — see docs/adr/0006.
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // AGP 7.4.2 calls this block `packagingOptions`, not `packaging`
    // (renamed in AGP 8.0) — see docs/adr/0006-jdk11-toolchain-downgrade.md.
    packagingOptions {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

dependencies {
    // --- Compose ---
    // Pinned to the BOM generation that pairs with Compose Compiler 1.5.14
    // (the last one compatible with Kotlin 1.9.24 pre-K2) — see docs/adr/0006.
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // activity-compose/lifecycle pinned older than latest: AGP 7.4.2's bundled
    // D8 crashes with a bare NullPointerException (not a clean version error)
    // dexing androidx.lifecycle:lifecycle-livedata-core 2.8.7's class files —
    // see docs/adr/0006-jdk11-toolchain-downgrade.md.
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
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
    // Framework. Ported/wrapped in mesh/crypto/NoiseSession.kt. It is NOT
    // published to Maven Central under any coordinate (verified: no tags, no
    // releases, plain pom.xml with a typo'd groupId "com.southerstorm") — the
    // com.southernstorm:noise-java:0.1.0 coordinate this project started with
    // does not exist. Consumed via JitPack instead, which builds straight
    // from the GitHub repo; "master-SNAPSHOT" tracks the repo's only branch
    // since it has no tags. See docs/adr/0006-jdk11-toolchain-downgrade.md
    // and docs/adr/0002-vendoring-and-porting-strategy.md.
    implementation("com.github.rweather:noise-java:master-SNAPSHOT")

    // --- QR (setup handshake, officer key exchange) ---
    // CameraX pinned to 1.3.1 for the same D8-crashes-on-newer-bytecode reason
    // as the lifecycle downgrade above — 1.4.1 dexes fine under AGP 8.x but
    // not AGP 7.4.2. See docs/adr/0006.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

// Room's exportSchema defaults to true (correct — see docs/PRD.md §9.1's
// "never fallbackToDestructiveMigration" rule) but needs an explicit output
// directory or the KSP processor just warns and skips it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
