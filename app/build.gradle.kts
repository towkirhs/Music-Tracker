plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.musictracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.musictracker"
        minSdk = 29
        targetSdk = 35
        // GitHub numbers every build; that becomes the version, so each update installs as "newer".
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 10 + buildNumber
        versionName = "0.$buildNumber"

        // Whisper is built for 64-bit ARM only (the Nothing Phone (2a) and almost all current phones).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // One fixed key for every build, so a new APK installs over the old one and keeps your library.
    // Personal app, so the password is simple on purpose.
    signingConfigs {
        create("musictracker") {
            storeFile = file("musictracker.keystore")
            storePassword = "musictracker"
            keyAlias = "musictracker"
            keyPassword = "musictracker"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("musictracker")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("musictracker")
        }
    }

    compileOptions {
        // NewPipe Extractor uses newer Java library APIs.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Screenshot tests (app/src/test) render the screens on the computer, no phone needed.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Built-in player: background playback with notification and lock-screen controls.
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Finds songs on YouTube Music and their audio streams, with no account or API key.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")
    // Album art.
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Tiny web server for the website on your home Wi-Fi.
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
