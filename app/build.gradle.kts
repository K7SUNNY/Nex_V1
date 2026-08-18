import java.util.Properties
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
}

abstract class GitVersionValueSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        try {
            val gitCheck = runCommand(listOf("git", "rev-parse", "--is-inside-work-tree"))
            if (gitCheck != "true") {
                return getFallbackVersion()
            }

            val commitMsg = runCommand(listOf("git", "log", "--grep=Stage [0-9]\\+", "-n", "1", "--pretty=format:%s"))
            val commitHash = runCommand(listOf("git", "log", "--grep=Stage [0-9]\\+", "-n", "1", "--pretty=format:%H"))

            val minor: String
            val patch: String

            if (commitMsg.isEmpty() || commitHash.isEmpty()) {
                minor = "0"
                patch = runCommand(listOf("git", "rev-list", "HEAD", "--count"))
            } else {
                val regex = Regex("\\bStage\\s+(\\d+)\\b")
                val match = regex.find(commitMsg)
                minor = match?.groupValues?.get(1) ?: "0"
                patch = runCommand(listOf("git", "rev-list", "${commitHash.trim()}..HEAD", "--count"))
            }

            val dirtyStatus = runCommand(listOf("git", "status", "--porcelain"))
            val isDirty = dirtyStatus.lines().any { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && !trimmed.endsWith("version.properties")
            }
            val suffix = if (isDirty) " ~ dirty" else ""

            return "1.${minor.trim()}.${patch.trim()}$suffix"
        } catch (e: Exception) {
            return getFallbackVersion()
        }
    }

    private fun runCommand(cmd: List<String>): String {
        val output = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(cmd)
            standardOutput = output
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        return if (result.exitValue == 0) output.toString().trim() else ""
    }

    private fun getFallbackVersion(): String {
        try {
            val file = File("app/version.properties")
            val rootFile = File("version.properties")
            val finalFile = if (file.exists()) file else if (rootFile.exists()) rootFile else null
            if (finalFile != null) {
                val props = Properties()
                finalFile.inputStream().use { props.load(it) }
                return props.getProperty("VERSION_NAME", "1.0.0")
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "1.0.0"
    }
}

val gitVersionProvider = providers.of(GitVersionValueSource::class.java) {}

val versionPropsFile = file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
} else {
    versionProps["VERSION_CODE"] = "1"
    versionProps["VERSION_NAME"] = "1.0.0"
}

android {
    namespace = "com.k7sunny.nexv1"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.k7sunny.nexv1"
        minSdk = 24
        targetSdk = 36
        versionCode = versionProps["VERSION_CODE"].toString().toInt()
        versionName = versionProps["VERSION_NAME"].toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("")
                arguments("-DGGML_OPENMP=ON", "-DGGML_LLAMAFILE=OFF")
                abiFilters("arm64-v8a")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.versionName.set(gitVersionProvider)
        }
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.tables)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

tasks.register("incrementVersion") {
    doLast {
        val properties = Properties()
        val file = file("version.properties")
        properties.load(file.inputStream())
        val currentCode = properties.getProperty("VERSION_CODE").toInt()
        properties.setProperty("VERSION_CODE", (currentCode + 1).toString())
        properties.store(file.outputStream(), null)
        println("Version code incremented to ${properties.getProperty("VERSION_CODE")}")
    }
}

tasks.named("preBuild") {
    dependsOn("incrementVersion")
}
