plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "vad.dashing.tbox"
    compileSdk = 36

    defaultConfig {
        applicationId = "vad.dashing.tbox"
        minSdk = 28
        targetSdk = 36
        versionCode = 1300
        versionName = "0.13.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "TBOX_PROXY_VERSION",
            "\"${libs.versions.tboxProxy.get()}\""
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
}

/**
 * OEM [libmbCan.so] ships with PT_LOAD p_align=4096; Google Play requires 16 KB ELF alignment
 * for apps targeting Android 15+. The script pads file offsets so (vaddr-offset) % 16384 == 0.
 */
tasks.register<Exec>("alignMbCanFor16KPageSize") {
    val script = rootProject.layout.projectDirectory.file("tools/align_elf_load_16k.py")
    val lib = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libmbCan.so")
    onlyIf { script.asFile.exists() && lib.asFile.exists() }
    commandLine(
        "python3",
        script.asFile.absolutePath,
        lib.asFile.absolutePath,
        "-o",
        lib.asFile.absolutePath,
    )
}

tasks.named("preBuild").configure {
    dependsOn("alignMbCanFor16KPageSize")
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
    implementation(libs.androidx.material)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation("com.github.jsparrow2006:tbox-proxy:v${libs.versions.tboxProxy.get()}")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}