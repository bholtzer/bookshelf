plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.bookshelf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bookshelf"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
}

dependencies {
    // ── Compose ──────────────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)                 // HorizontalPager
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // ── Navigation ───────────────────────────────────────────────────────────
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // ── Hilt DI ──────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ── Room ─────────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Firebase ─────────────────────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)

    // ── Google Sign-In (Credential Manager) ──────────────────────────────────
    implementation(libs.google.id)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)

    // ── Image loading (Coil) ─────────────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.pdf)                           // PDF thumbnail support

    // ── WorkManager (background sync) ────────────────────────────────────────
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)   // await() for Firebase Tasks

    // ── DataStore (token / session prefs) ────────────────────────────────────
    implementation(libs.datastore.preferences)
}
