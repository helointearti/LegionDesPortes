import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.legiondesportes"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.legiondesportes"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Clé fixe : chaque nouvelle version s'installe par-dessus la précédente
    signingConfigs {
        create("legion") {
            storeFile = file("legion.keystore")
            storePassword = "legion123"
            keyAlias = "legion"
            keyPassword = "legion123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("legion")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("legion")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
