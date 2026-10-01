plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "vad.dashing.voice"
    compileSdk = 36

    defaultConfig {
        applicationId = "vad.dashing.voice"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // HU ABIs only — drop emulator x86 from the 47 MB sherpa AAR.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
        // OfflineTts JNI only needs onnxruntime + sherpa-onnx-jni.
        jniLibs {
            excludes += listOf(
                "**/libsherpa-onnx-c-api.so",
                "**/libsherpa-onnx-cxx-api.so",
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    // Piper / VITS offline TTS (JNI + onnxruntime inside AAR).
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.5")

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation(libs.androidx.ui.tooling)
}

// Piper model is large — not in git. Download+extract in Gradle (no Python; works on Windows).
val ttsModelDirName = "vits-piper-ru_RU-irina-medium-int8"
val ttsModelUrl =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
        "vits-piper-ru_RU-irina-medium-int8.tar.bz2"
val ttsAssetsDir = layout.projectDirectory.dir("src/main/assets")
val ttsModelMarker = ttsAssetsDir.file("$ttsModelDirName/tokens.txt")
val ttsModelDirFile = ttsAssetsDir.file(ttsModelDirName).asFile
val ttsDownloadCacheFile =
    layout.buildDirectory.file("tts-models/vits-piper-ru_RU-irina-medium-int8.tar.bz2").get().asFile

val fetchTtsModel by tasks.registering {
    description = "Download Piper RU Irina int8 into voice assets (no Python required)"
    notCompatibleWithConfigurationCache("Uses Ant get/untar at execution time")
    val marker = ttsModelMarker.asFile
    val assetsDir = ttsAssetsDir.asFile
    val modelDir = ttsModelDirFile
    val archive = ttsDownloadCacheFile
    val modelUrl = ttsModelUrl
    outputs.file(marker)
    doLast {
        if (marker.isFile) {
            logger.lifecycle("TTS model already present: ${marker.parentFile}")
            return@doLast
        }
        archive.parentFile.mkdirs()
        assetsDir.mkdirs()
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }
        logger.lifecycle("Downloading Piper TTS model…")
        project.ant.invokeMethod(
            "get",
            mapOf(
                "src" to modelUrl,
                "dest" to archive.absolutePath,
            ),
        )
        logger.lifecycle("Extracting into $assetsDir")
        project.ant.invokeMethod(
            "untar",
            mapOf(
                "src" to archive.absolutePath,
                "dest" to assetsDir.absolutePath,
                "compression" to "bzip2",
            ),
        )
        check(marker.isFile) {
            "TTS extract failed: missing ${marker.absolutePath}"
        }
        val onnx = modelDir.resolve("ru_RU-irina-medium.onnx")
        val espeak = modelDir.resolve("espeak-ng-data")
        check(onnx.isFile && espeak.isDirectory) {
            "TTS extract incomplete: onnx or espeak-ng-data missing under $modelDir"
        }
        logger.lifecycle("OK: $modelDir")
    }
}

tasks.matching {
    val n = it.name
    n.startsWith("assemble") ||
        n.startsWith("merge") && n.endsWith("Assets") ||
        n.startsWith("package") && n.endsWith("Assets")
}.configureEach {
    dependsOn(fetchTtsModel)
}
