import java.util.Properties

plugins {
    id("com.android.application")
    // org.jetbrains.kotlin.android is no longer applied here -- AGP 9 has built-in
    // Kotlin support. See the buildscript block in the root build.gradle.kts for
    // pinning the Kotlin Gradle Plugin version above AGP's bundled default.
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

// Active environment config — set by switch-env.ps1, never committed
val envPropertiesFile = rootProject.file("environments/active.properties")
val envProperties = Properties()
if (envPropertiesFile.exists()) {
    envProperties.load(envPropertiesFile.inputStream())
}

android {
    namespace = "com.cherryblossomdev.breakroom"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cherryblossomdev.breakroom"
        minSdk = 24
        targetSdk = 37
        versionCode = 17
        versionName = "1.10.0"

        // Backend API version this app was designed to work with.
        // Informational only - used for debugging compatibility issues.
        buildConfigField("String", "COMPATIBLE_API_VERSION", "\"1.0.0\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        debug {
            val debugUrl = envProperties.getProperty("BASE_URL", "http://10.0.2.2:3001/")
            buildConfigField("String", "BASE_URL", "\"$debugUrl\"")
        }
        create("dev") {
            initWith(getByName("debug"))
            buildConfigField("String", "BASE_URL", "\"https://test.dev.prosaurus.com/\"")
        }
        create("productionTest") {
            initWith(getByName("debug"))
            buildConfigField("String", "BASE_URL", "\"https://test.prosaurus.com/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "BASE_URL", "\"https://www.prosaurus.com/\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Extended Material Icons (for additional icons like Article, ChatBubbleOutline, etc.)
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Retrofit + OkHttp for networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Encrypted SharedPreferences for secure token storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Socket.IO Client for real-time chat
    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "org.json", module = "json")
    }

    // ExoPlayer for audio playback (supports WebM/Opus, M4A, etc.)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.2.1")

    // Image loading with Coil for Compose
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Lifecycle Service for foreground service
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-messaging")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // Play Console flagged androidx.fragment:fragment:1.1.0 as outdated -- it's a
    // transitive dependency of com.google.android.gms:play-services-base (pulled in by
    // both billing-ktx and firebase-messaging), not something declared directly here.
    // Force the resolved version up since the transitive one is stuck on 1.1.0.
    implementation("androidx.fragment:fragment:1.8.9")
}
