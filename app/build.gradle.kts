plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

import java.io.File
import java.io.RandomAccessFile
import java.util.Properties
import org.gradle.api.tasks.Exec

val appGitCommitCount = providers.exec {
    workingDir(rootDir)
    commandLine("git", "rev-list", "--count", "HEAD", "--", "app")
    isIgnoreExitValue = true
}.standardOutput.asText.map { output ->
    output.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
}

android {
    namespace = "com.bocchi.iconeditor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bocchi.iconeditor"
        minSdk = 33
        targetSdk = 36
        versionCode = appGitCommitCount.get()
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
            isShrinkResources = true
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
    val aapt2Dir = layout.projectDirectory.dir("export_toolchain")
    commandLine("bash", script.absolutePath)
    inputs.file(script)
    inputs.files(
        aapt2Dir.file("aapt2-arm64"),
        aapt2Dir.file("aapt2-x86_64"),
    )
    outputs.file(layout.projectDirectory.file("src/main/assets/export_toolchain/android-35.jar"))
}

val prepareAapt2JniLibs by tasks.registering {
    dependsOn(bundleExportToolchain)
    val aapt2Dir = project.layout.projectDirectory.dir("export_toolchain")
    val generatedDir = project.layout.buildDirectory.dir("generated/aapt2-jni")
    inputs.dir(aapt2Dir)
    outputs.dir(generatedDir)
    doLast {
        fun verifyLoadAlignment(src: File) {
            val requiredAlignment = 16L * 1024L
            RandomAccessFile(src, "r").use { elf ->
                fun readUnsignedShort(offset: Long): Int {
                    elf.seek(offset)
                    return elf.readUnsignedByte() or (elf.readUnsignedByte() shl 8)
                }

                fun readUnsignedInt(offset: Long): Long {
                    elf.seek(offset)
                    var value = 0L
                    repeat(4) { byteIndex ->
                        value = value or (elf.readUnsignedByte().toLong() shl (byteIndex * 8))
                    }
                    return value
                }

                fun readLong(offset: Long): Long {
                    elf.seek(offset)
                    var value = 0L
                    repeat(8) { byteIndex ->
                        value = value or (elf.readUnsignedByte().toLong() shl (byteIndex * 8))
                    }
                    return value
                }

                check(elf.length() >= 64L) { "Invalid ELF file: $src" }
                val ident = ByteArray(16)
                elf.readFully(ident)
                check(
                    ident[0] == 0x7f.toByte() &&
                        ident[1] == 'E'.code.toByte() &&
                        ident[2] == 'L'.code.toByte() &&
                        ident[3] == 'F'.code.toByte() &&
                        ident[4] == 2.toByte() &&
                        ident[5] == 1.toByte(),
                ) { "Expected a little-endian ELF64 file: $src" }

                val programHeaderOffset = readLong(32L)
                val programHeaderSize = readUnsignedShort(54L)
                val programHeaderCount = readUnsignedShort(56L)
                check(programHeaderSize >= 56 && programHeaderCount > 0) {
                    "Invalid ELF program headers: $src"
                }
                check(
                    programHeaderOffset >= 64L &&
                        programHeaderOffset + programHeaderSize.toLong() * programHeaderCount <= elf.length(),
                ) { "ELF program headers exceed file bounds: $src" }

                var loadSegmentCount = 0
                repeat(programHeaderCount) { index ->
                    val headerOffset = programHeaderOffset + index.toLong() * programHeaderSize
                    if (readUnsignedInt(headerOffset) == 1L) {
                        loadSegmentCount++
                        val alignment = readLong(headerOffset + 48L)
                        check(alignment >= requiredAlignment && alignment % requiredAlignment == 0L) {
                            "$src has a PT_LOAD alignment of 0x${alignment.toString(16)}; " +
                                "all load segments must be aligned to at least 0x4000"
                        }
                    }
                }
                check(loadSegmentCount > 0) { "ELF file has no PT_LOAD segments: $src" }
            }
        }

        fun install(assetName: String, abiDir: String) {
            val src = aapt2Dir.file(assetName).asFile
            check(src.isFile) { "Missing aapt2 asset: $src" }
            verifyLoadAlignment(src)
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

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    debugImplementation(libs.compose.ui.tooling)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    implementation(libs.apksig)
}
