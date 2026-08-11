plugins {
    id("com.android.application")
}

android {
    namespace = "dev.inkysketch.app"
    compileSdk = 35

    defaultConfig {
        // Permanent legacy ID: retaining it preserves upgrades and app-private drawing data.
        applicationId = "dev.einkstudio.poc"
        minSdk = 26
        targetSdk = 30
        versionCode = 3
        versionName = "0.3.0-alpha.1"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("INKY_SKETCH_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
            }
            storePassword = System.getenv("INKY_SKETCH_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("INKY_SKETCH_KEY_ALIAS")
            keyPassword = System.getenv("INKY_SKETCH_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        // BOOX raw-pen compatibility currently relies on the established target behavior.
        disable += "ExpiredTargetSdkVersion"
    }

    packaging {
        jniLibs {
            pickFirsts += "lib/**/libc++_shared.so"
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/ASL2.0"
            )
        }
    }
}

dependencies {
    implementation("com.onyx.android.sdk:onyxsdk-pen:1.5.4")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
