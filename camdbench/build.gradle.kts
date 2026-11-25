plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

extra["apkSlug"] = "camdbench"
apply(from = rootProject.file("gradle/apk-release.gradle"))

android {
    namespace = "vad.dashing.camdbench"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "vad.dashing.camdbench"
        minSdk = 28
        targetSdk = 36
        versionCode = 3

        versionName = "1.1.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// The sampler is copied into assets by CMake; each variant must wait on its own native build.
tasks.matching { it.name == "mergeDebugAssets" }.configureEach {
    dependsOn("externalNativeBuildDebug")
}
tasks.matching { it.name == "mergeReleaseAssets" }.configureEach {
    dependsOn("externalNativeBuildRelease")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
}
