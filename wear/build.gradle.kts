plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

// Wear OS app. It talks to the phone app over the Wear Data Layer, which only connects apps with the
// same applicationId and signing key, so this mirrors app/build.gradle.kts in both.
android {
    namespace = "app.tick.kimai.wear"
    compileSdk = 36
    // Must match buildToolsVersion in flake.nix
    buildToolsVersion = "36.0.0"

    val releaseVersion = System.getenv("RELEASE_VERSION")?.removePrefix("v") ?: "0.1.0"
    val (versionMajor, versionMinor, versionPatch) = releaseVersion.split(".").map { it.toInt() }

    defaultConfig {
        applicationId = "app.tick.kimai"
        minSdk = 30
        targetSdk = 35
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = releaseVersion
    }

    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    if (releaseKeystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    applicationVariants.all {
        outputs.all {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                outputFileName = "tick-wear-$versionName.apk"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout.material)
    implementation(libs.concurrent.futures.ktx)
    implementation(libs.play.services.wearable)
}
