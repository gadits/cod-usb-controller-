plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.elad.codusb"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elad.codusb"
        minSdk = 30 // Wireless debugging exists from Android 11
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // Fixed key so every new build installs as an update over the old one
    signingConfigs {
        create("shared") {
            storeFile = file("codusb.keystore")
            storePassword = "codusb123"
            keyAlias = "codusb"
            keyPassword = "codusb123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // ADB client (wireless pairing + connection) that runs inside the app
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    // Self-signed certificate for the ADB key
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
}
