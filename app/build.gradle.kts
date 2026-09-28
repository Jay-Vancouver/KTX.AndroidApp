plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from ~/.gradle/gradle.properties or environment variables of the same
// name, never from this repository (docs/SIGNING.md). Without them assembleRelease is unsigned.
fun signingValue(name: String): String? =
    providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

val releaseKeystore = signingValue("KTX_KEYSTORE_FILE")

android {
    namespace = "com.ktxtransport.driver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ktxtransport.driver"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "START_URL", "\"https://driver.withktx.com/\"")
        buildConfigField("String", "VERSION_URL", "\"https://driver.withktx.com/app/version.json\"")
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

// Release APK named for the download page: ktx-driver-<versionName>.apk
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName?.set("ktx-driver-${output.versionName.get()}.apk")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
