import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
}

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
            // ADD THIS LINE:
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
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
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
