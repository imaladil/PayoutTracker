plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The Firebase plugin is applied only when app/google-services.json exists, so the app still
// builds (without online features) before Firebase has been set up.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val canSign = file("release.jks").exists() && System.getenv("KEYSTORE_PASSWORD") != null

android {
    namespace = "com.payouttracker.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.payouttracker.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "1.11"
    }

    // The signing key is NOT stored in the repository. GitHub Actions restores it from
    // repository secrets into app/release.jks and passes the passwords as environment variables.
    // Every update must be signed with this same key, otherwise Android refuses to install it
    // over the existing app.
    if (canSign) {
        signingConfigs {
            create("release") {
                storeFile = file("release.jks")
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "payout"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the key (for example a build outside GitHub) fall back to the debug key
            // so the build still produces an installable app-release.apk.
            signingConfig = if (canSign) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Online features: Google sign-in and the shared database
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
