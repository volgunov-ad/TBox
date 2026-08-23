plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.io.FileInputStream
import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
/** Built-in MapKit key; override with MAPKIT_API_KEY in local.properties or in-app setting. */
val mapkitApiKeyDefault = "ea04053c-558f-419e-8ccc-1a2bc3d3fcf0"
val mapkitApiKeyRaw = localProperties.getProperty("MAPKIT_API_KEY", "").ifBlank {
    mapkitApiKeyDefault
}
val mapkitApiKeyFromLocal =
    mapkitApiKeyRaw.replace("\\", "\\\\").replace("\"", "\\\"")
/**
 * Flip to `true` to ship Yandex MapKit (~26 MB native lib per ABI) and the
 * online basemap toggle. Off = Canvas-only road-match map; SDK code stays in
 * `src/mapkitEnabled/` for a later re-enable.
 */
val mapkitEnabled = false

android {
    namespace = "vad.dashing.tbox"
    compileSdk = 36

    defaultConfig {
        applicationId = "vad.dashing.tbox"
        minSdk = 28
        targetSdk = 36
        versionCode = 1814
        versionName = "0.18.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "TBOX_PROXY_VERSION",
            "\"${libs.versions.tboxProxy.get()}\""
        )
        buildConfigField(
            "String",
            "UPDATE_RELEASE_PUBLIC_KEY",
            "\"https://disk.yandex.ru/d/v-6n17wSRbVQsw\""
        )
        buildConfigField(
            "String",
            "UPDATE_DEV_PUBLIC_KEY",
            "\"https://disk.yandex.ru/d/yuvH_9cdzyOoBg\""
        )
        buildConfigField(
            "String",
            "UPDATE_SIGNING_CERT_SHA256",
            "\"\""
        )
        buildConfigField(
            "String",
            "MAPKIT_API_KEY",
            "\"$mapkitApiKeyFromLocal\"",
        )
        buildConfigField(
            "boolean",
            "MAPKIT_ENABLED",
            mapkitEnabled.toString(),
        )
    }
    flavorDimensions += "language"
    productFlavors {
        create("ru") {
            dimension = "language"
            versionNameSuffix = "-ru"
        }
        create("en") {
            dimension = "language"
            versionNameSuffix = "-en"
        }
    }
    signingConfigs {
        // Same debug key on PC and Cursor Cloud so APKs install over each other.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        getByName("main") {
            java.srcDir(
                if (mapkitEnabled) "src/mapkitEnabled/java" else "src/mapkitDisabled/java",
            )
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.protolite.well.known.types)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.material)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.profileinstaller.profileinstaller)
    implementation(libs.okhttp)
    implementation(libs.snakeyaml.engine)
    if (mapkitEnabled) {
        implementation(libs.yandex.mapkit.lite)
    }
    implementation("com.github.jsparrow2006:tbox-proxy:v${libs.versions.tboxProxy.get()}")
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}