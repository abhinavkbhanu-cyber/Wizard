plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rb22"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rb22"
        minSdk = 24
        targetSdk = 35
        versionCode = 15
        versionName = "1.1-beta"
    }

    signingConfigs {
        create("rb22Release") {
            storeFile = file("rb22-release.jks")
            storePassword = System.getenv("RB22_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("RB22_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("RB22_KEY_PASSWORD") ?: ""
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rb22Release")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
