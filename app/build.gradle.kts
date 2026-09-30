import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from ~/.gradle/gradle.properties or environment variables of the same
// name, never from this repository (docs/SIGNING.md). Without them assembleRelease is unsigned.
fun signingValue(name: String): String? =
    providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

val releaseKeystore = signingValue("KTX_KEYSTORE_FILE")

// Admin PIN for changing the server address in the app (SettingsActivity). Like the signing
// values it lives outside the repo (KTX_ADMIN_PIN); only its SHA-256 goes into the APK.
val adminPinSha256: String = (signingValue("KTX_ADMIN_PIN") ?: "000000").let { pin ->
    if (signingValue("KTX_ADMIN_PIN") == null) logger.warn("KTX_ADMIN_PIN not set; using 000000")
    MessageDigest.getInstance("SHA-256").digest(pin.trim().toByteArray())
        .joinToString("") { "%02x".format(it) }
}

android {
    namespace = "com.ktxtransport.driver"
    // 36 (Android 16): Google Play requires new apps to target a recent API level.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ktxtransport.driver"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "START_URL", "\"https://driver.withktx.com/\"")
        buildConfigField("String", "VERSION_URL", "\"https://driver.withktx.com/app/version.json\"")
        buildConfigField("String", "ADMIN_PIN_SHA256", "\"$adminPinSha256\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("KTX_KEYSTORE_PASSWORD")
                keyAlias = signingValue("KTX_KEY_ALIAS")
                keyPassword = signingValue("KTX_KEY_PASSWORD")
            }
        }
    }

    // Same app (package, key, version) built two ways:
    //   direct - APK from the company site: in-app updates, direct battery-exemption request
    //   play   - AAB for Google Play: Play updates the app; no install or battery-exemption permission
    // Flavor-only code and manifest entries live in src/direct and src/play.
    flavorDimensions += "distribution"
    productFlavors {
        create("direct") {
            dimension = "distribution"
            buildConfigField("boolean", "DIRECT_BATTERY_REQUEST", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "DIRECT_BATTERY_REQUEST", "false")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Release APKs named for the download page: ktx-driver-<versionName>.apk (direct),
// ktx-driver-<versionName>-play.apk (play; Google Play takes the AAB instead).
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val suffix = if (variant.flavorName == "play") "-play" else ""
        variant.outputs.forEach { output ->
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName?.set("ktx-driver-${output.versionName.get()}$suffix.apk")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
