import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "vad.dashing.store"
    compileSdk = 36

    defaultConfig {
        applicationId = "vad.dashing.store"
        minSdk = 28
        targetSdk = 36
        // Keep in sync with store/Changelog.dm (bump both on every Disk publish).
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Dedicated AppStore shares (release/ + dev/ apps trees). Not the Monitor OTA keys.
        buildConfigField(
            "String",
            "STORE_PUBLIC_KEY",
            "\"https://disk.yandex.ru/d/KDaHLxbFtPAjYA\"",
        )
        // Mail.ru Cloud mirror weblink (folder listing: /api/v2/folder?weblink=…).
        // Client download fallback — see docs/APP_STORE_PLAN_RU.md §9 этап 7.
        buildConfigField(
            "String",
            "STORE_MAILRU_WEBLINK",
            "\"voi3/4Lx1RhqV9\"",
        )
        buildConfigField(
            "String",
            "STORE_MAILRU_PUBLIC_URL",
            "\"https://cloud.mail.ru/public/voi3/4Lx1RhqV9\"",
        )
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    applicationVariants.all {
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName =
                "tbox-store-${versionName}-${buildType.name}.apk"
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.okhttp)
    implementation(libs.zxing)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation("org.json:json:20240303")
    debugImplementation(libs.androidx.ui.tooling)
}
