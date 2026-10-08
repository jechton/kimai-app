plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "app.tick.kimai"
    compileSdk = 35
    // Must match buildToolsVersion in flake.nix
    buildToolsVersion = "35.0.0"

    // CI sets RELEASE_VERSION from the pushed tag (e.g. "v1.2.3"); local builds fall back below.
    val releaseVersion = System.getenv("RELEASE_VERSION")?.removePrefix("v") ?: "0.1.0"
    val (versionMajor, versionMinor, versionPatch) = releaseVersion.split(".").map { it.toInt() }

    defaultConfig {
        applicationId = "app.tick.kimai"
        minSdk = 26
        targetSdk = 35
        // Must stay monotonically increasing across releases (Android/Obtainium update checks).
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = releaseVersion
    }

    // Set by CI from repo secrets (see .github/workflows/release.yml). Release builds without
    // them are unsigned, which is fine for local assembleRelease but can't be installed as-is.
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
                outputFileName = "tick-${versionName}.apk"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.work)
    implementation(libs.zxing.embedded)
}
