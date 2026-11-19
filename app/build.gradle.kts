plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de version : fourni automatiquement par GitHub Actions (-PversionCode=N), sinon 1 en local.
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0-local"
// Dépôt GitHub qui publie les mises à jour : rempli automatiquement par GitHub Actions.
val githubRepo = System.getenv("GITHUB_REPOSITORY") ?: "OWNER/REPO"

android {
    namespace = "com.cgsi.clavier"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cgsi.clavier"
        minSdk = 28          // BluetoothHidDevice exige Android 9 (API 28) minimum
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
    }

    // Une seule clé de signature pour toutes les versions : indispensable pour que les mises à jour s'installent.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/clavier.jks")
            storePassword = "clavier2026"
            keyAlias = "clavier"
            keyPassword = "clavier2026"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
