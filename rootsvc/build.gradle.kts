import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

extra["apkSlug"] = "rootsvc"
apply(from = rootProject.file("gradle/apk-release.gradle"))

android {
    namespace = "vad.dashing.rootsvc"
    compileSdk = 36

    defaultConfig {
        applicationId = "vad.dashing.rootsvc"
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"
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
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            java.srcDir("../shared/rootsvc/java")
        }
    }
}

abstract class BuildDaemonDexTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classesDir: DirectoryProperty

    @get:Input
    abstract val d8Executable: Property<String>

    @get:Input
    abstract val androidJarPath: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun run() {
        val dest = outputDir.get().asFile
        dest.mkdirs()
        dest.listFiles()?.forEach { it.delete() }
        val classes = classesDir.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map { it.absolutePath }
            .toList()
        require(classes.isNotEmpty()) { "No daemon classes compiled" }
        execOps.exec {
            commandLine(
                listOf(
                    d8Executable.get(),
                    "--min-api", "28",
                    "--lib", androidJarPath.get(),
                    "--output", dest.absolutePath,
                ) + classes,
            )
        }
    }
}

val androidJar: File =
    File(android.sdkDirectory, "platforms/android-36/android.jar").also {
        require(it.isFile) { "Missing $it — install platforms;android-36" }
    }
val d8: File = run {
    val tools = File(android.sdkDirectory, "build-tools").listFiles()
        ?.filter { it.isDirectory && !it.name.contains("rc", ignoreCase = true) }
        ?.sortedBy { it.name }
        ?: emptyList()
    val dir = tools.lastOrNull()
        ?: error("No stable build-tools under ${android.sdkDirectory}/build-tools")
    val bat = dir.resolve("d8.bat")
    val bin = dir.resolve("d8")
    when {
        bat.isFile -> bat
        bin.isFile -> bin
        else -> error("d8 not found in $dir")
    }
}

val daemonClasses = layout.buildDirectory.dir("daemon/classes")
val daemonDexOut = layout.buildDirectory.dir("daemon/dex")

val compileDaemonJava by tasks.registering(JavaCompile::class) {
    group = "build"
    description = "Compile rootsvc app_process daemon against android.jar"
    source = fileTree("daemon/src") {
        include("**/*.java")
    }
    // Shared client (WireCodec / Allowlist / Protocol) ships inside the same dex
    // so the boot script can PING with CLASSPATH=rootsvc.dex.
    source(fileTree("../shared/rootsvc/java") {
        include("**/*.java")
    })
    classpath = files(androidJar)
    destinationDirectory.set(daemonClasses)
    options.encoding = "UTF-8"
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

val buildDaemonDex by tasks.registering(BuildDaemonDexTask::class) {
    group = "build"
    description = "d8 rootsvc.dex into assets"
    dependsOn(compileDaemonJava)
    classesDir.set(daemonClasses)
    d8Executable.set(d8.absolutePath)
    androidJarPath.set(androidJar.absolutePath)
    outputDir.set(daemonDexOut)
}

val copyDaemonDex by tasks.registering(Copy::class) {
    dependsOn(buildDaemonDex)
    from(daemonDexOut.map { it.file("classes.dex") })
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "rootsvc.dex" }
}

/** Force LF in shell assets so Windows checkouts / editors cannot ship CRLF into the APK. */
val normalizeShellAssets by tasks.registering {
    group = "build"
    description = "Rewrite rootsvc shell assets as LF-only"
    dependsOn(copyDaemonDex)
    val assetsDir = layout.projectDirectory.dir("src/main/assets")
    inputs.files(fileTree(assetsDir) { include("*.sh") })
    outputs.files(fileTree(assetsDir) { include("*.sh") })
    doLast {
        assetsDir.asFile.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".sh") }
            ?.forEach { file ->
                val text = file.readText(Charsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                file.writeText(text, Charsets.UTF_8)
            }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(normalizeShellAssets)
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(normalizeShellAssets)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
