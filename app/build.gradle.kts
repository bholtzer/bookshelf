import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { stream -> load(stream) }
    }
}

fun googleWebClientId(): String =
    providers.gradleProperty("GOOGLE_WEB_CLIENT_ID").orNull
        ?: localProperties.getProperty("GOOGLE_WEB_CLIENT_ID")
        ?: System.getenv("GOOGLE_WEB_CLIENT_ID")
        ?: ""

android {
    namespace = "com.bihstudio.bookshelf"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bihstudio.bookshelf"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId()}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "ENVIRONMENT", "\"development\"")
            buildConfigField("boolean", "IS_PRODUCTION", "false")
            buildConfigField("boolean", "ENABLE_FIREBASE_APP_CHECK", "false")
        }

        release {
            isMinifyEnabled = false
            buildConfigField("String", "ENVIRONMENT", "\"production\"")
            buildConfigField("boolean", "IS_PRODUCTION", "true")
            buildConfigField("boolean", "ENABLE_FIREBASE_APP_CHECK", "true")
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    debugImplementation(libs.firebase.appcheck.debug)
    releaseImplementation(libs.firebase.appcheck.playintegrity)

    // Google Sign-In
    implementation(libs.google.id)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)

    // Coil
    implementation(libs.coil.compose)

    // WorkManager + Hilt
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // DataStore
    implementation(libs.datastore.preferences)

    // Google Play deferred invite links
    implementation(libs.install.referrer)
    implementation(libs.billing.client)
}
