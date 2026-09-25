plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "mx.sntss1puebla.credenciales"
    compileSdk = 36

    defaultConfig {
        applicationId = "mx.sntss1puebla.credenciales"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
    }

    // Clave permanente: se toma de variables de entorno (secrets de GitHub), nunca del repositorio.
    val keystorePath = System.getenv("ANDROID_KEYSTORE_FILE")
    val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val hasSharedKey = !keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()

    signingConfigs {
        if (hasSharedKey) {
            create("shared") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "radiosindical"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            if (hasSharedKey) signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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
    implementation("androidx.activity:activity-ktx:1.12.3")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("com.google.guava:guava:33.4.8-android")
}
