plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "vad.dashing.rootclient"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDir("../shared/rootsvc/java")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
