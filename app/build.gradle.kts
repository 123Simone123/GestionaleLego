plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.brickarchivio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brickarchivio.app"
        minSdk = 26
        targetSdk = 35
        // In CI il numero di versione cresce a ogni build (GITHUB_RUN_NUMBER),
        // così ogni APK si installa sopra il precedente.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    signingConfigs {
        // Chiave fissa inclusa nel repo: l'app è personale e locale (scelta dell'utente).
        // Serve a far sì che ogni nuova build si installi sopra la precedente senza perdere i dati.
        create("fixed") {
            storeFile = file("keystore/brickarchivio.jks")
            storePassword = "brickarchivio"
            keyAlias = "brickarchivio"
            keyPassword = "brickarchivio"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Database locale
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Rete e immagini
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Riconoscimento sul telefono (modelli inclusi nell'APK, funzionano offline)
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.android.gms:play-services-tasks:18.2.0")

    testImplementation("junit:junit:4.13.2")
}
