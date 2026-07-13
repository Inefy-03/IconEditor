plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

import java.util.Properties
import org.gradle.api.tasks.Exec

android {
    namespace = "com.bocchi.iconeditor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bocchi.iconeditor"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties().apply {
            load(keystorePropertiesFile.inputStream())
        }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file("mykey.keystore")
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("$projectDir/build/generated/aapt2-jni")
        }
    }
}

val bundleExportToolchain by tasks.registering(Exec::class) {
    val script = rootProject.file("scripts/fetch_export_toolchain.sh")
    commandLine("bash", script.absolutePath)
    inputs.file(script)
    outputs.dir(layout.projectDirectory.dir("src/main/assets/export_toolchain"))
}

val prepareAapt2JniLibs by tasks.registering {
    dependsOn(bundleExportToolchain)
    val assetDir = project.layout.projectDirectory.dir("src/main/assets/export_toolchain")
    val generatedDir = project.layout.buildDirectory.dir("generated/aapt2-jni")
    inputs.dir(assetDir)
    outputs.dir(generatedDir)
    doLast {
        fun install(assetName: String, abiDir: String) {
            val src = assetDir.file(assetName).asFile
            check(src.isFile) { "缺少 aapt2 资源：$src" }
            val dir = generatedDir.get().dir(abiDir).asFile
            dir.mkdirs()
            src.copyTo(dir.resolve("libiconeditor_aapt2.so"), overwrite = true)
        }
        install("aapt2-arm64", "arm64-v8a")
        install("aapt2-x86_64", "x86_64")
    }
}

tasks.named("preBuild") {
    dependsOn(bundleExportToolchain, prepareAapt2JniLibs)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.navigation3.ui)

    debugImplementation(libs.compose.ui.tooling)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    implementation(libs.apksig)
}
