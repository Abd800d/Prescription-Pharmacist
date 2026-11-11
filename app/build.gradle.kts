import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firebase is optional: the google-services plugin is applied ONLY when the
// developer provides app/google-services.json (which is gitignored).
// The app builds and runs in "local mode" without any Firebase configuration.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Optional non-secret configuration from local.properties (never committed).
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localOrGradleProperty(key: String): String =
    localProperties.getProperty(key) ?: (project.findProperty(key) as? String) ?: ""

android {
    namespace = "com.maakthi.prescriptionpharmacist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maakthi.prescriptionpharmacist"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Base URL of the secure analysis backend (NOT a secret).
        // Can be overridden at runtime from the in-app Settings screen.
        buildConfigField(
            "String",
            "ANALYSIS_BASE_URL",
            "\"${localOrGradleProperty("ANALYSIS_BASE_URL")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release is signed only when the developer supplies a keystore in
            // local.properties (see README). Otherwise the build stays unsigned.
            val keystorePath = localOrGradleProperty("RELEASE_STORE_FILE")
            if (keystorePath.isNotEmpty()) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = rootProject.file(keystorePath)
                    storePassword = localOrGradleProperty("RELEASE_STORE_PASSWORD")
                    keyAlias = localOrGradleProperty("RELEASE_KEY_ALIAS")
                    keyPassword = localOrGradleProperty("RELEASE_KEY_PASSWORD")
                }
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
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
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Core / lifecycle / navigation
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material)

    // Coroutines
    implementation(libs.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.exifinterface)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // DataStore (settings)
    implementation(libs.datastore.preferences)

    // Firebase (used only when google-services.json is present)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.auth)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
