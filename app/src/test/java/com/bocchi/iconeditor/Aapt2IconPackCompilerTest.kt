package com.bocchi.iconeditor

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import com.bocchi.iconeditor.data.Aapt2IconPackCompiler
import com.bocchi.iconeditor.data.Aapt2Toolchain
import com.bocchi.iconeditor.data.IconMappingBridge
import com.bocchi.iconeditor.model.ApkInfo
import com.bocchi.iconeditor.model.IconMappingEntry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.imageio.ImageIO

class Aapt2IconPackCompilerTest {
    @Test
    fun buildAndSignApkWithEmbeddedApksig() {
        val hostToolchain = Aapt2Toolchain.resolveHostToolchain()
        assumeTrue("需要本机 ANDROID_HOME aapt2", hostToolchain != null)
        val workingDir = File(checkNotNull(System.getProperty("user.dir")))
        val moduleDir = if (File(workingDir, "src/main").isDirectory) {
            workingDir
        } else {
            File(workingDir, "app")
        }
        val packagedFramework = File(
            moduleDir,
            "src/main/assets/export_toolchain/android-35.jar",
        )
        assertTrue("Missing packaged framework resource jar", packagedFramework.isFile)
        val resolved = requireNotNull(hostToolchain).copy(androidJar = packagedFramework)

        val workDir = java.nio.file.Files.createTempDirectory("iconeditor-aapt2").toFile()
        val unsigned = File(workDir, "unsigned.apk")
        val signed = File(workDir, "signed.apk")
        File(workDir, "icons/res/drawable-xxhdpi/com.example.app.png").also { icon ->
            requireNotNull(icon.parentFile).mkdirs()
            icon.writeBytes(validPngBytes())
        }
        val mapping = listOf(
            IconMappingEntry(
                packageName = "com.example.app",
                drawableName = "i12345678",
                components = listOf("ComponentInfo{com.example.app/com.example.app.MainActivity}"),
            ),
        )
        val appfilter = IconMappingBridge.buildAppfilterXml(mapping)
        val drawableXml = IconMappingBridge.buildDrawableXml(mapping)
        val apkInfo = ApkInfo(
            packageName = "com.example.iconpack",
            versionName = "1.0",
            versionCode = 1,
            label = "Test Pack",
        )

        Aapt2IconPackCompiler.buildUnsignedApk(
            output = unsigned,
            apkInfo = apkInfo,
            workDir = workDir,
            icons = emptyList(),
            mapping = mapping,
            appfilter = appfilter,
            drawableXml = drawableXml,
            cacheDir = workDir,
            toolchainOverride = resolved,
        )

        assertTrue(unsigned.isFile)
        assertTrue(unsigned.length() > 0L)

        val ks = File(moduleDir, "src/main/assets/archive_templates/apk/export_keystore.jks")
        assertTrue("Missing packaged export keystore", ks.isFile)
        val password = "iconeditor".toCharArray()
        val keystore = KeyStore.getInstance("PKCS12").apply {
            ks.inputStream().use { load(it, password) }
        }
        val alias = "iconeditor_export"
        val privateKey = keystore.getKey(alias, password) as PrivateKey
        val certificate = keystore.getCertificate(alias) as X509Certificate
        ApkSigner.Builder(
            listOf(
                ApkSigner.SignerConfig.Builder(
                    alias,
                    KeyConfig.Jca(privateKey),
                    listOf(certificate),
                ).build(),
            ),
        )
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setMinSdkVersion(21)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
        assertTrue("Embedded apksig did not produce a signed APK", signed.isFile && signed.length() > 0L)
        val verification = ApkVerifier.Builder(signed)
            .setMinCheckedPlatformVersion(21)
            .build()
            .verify()
        assertTrue("Embedded apksig verification failed: ${verification.errors}", verification.isVerified)
        assertTrue("Missing v1 signature", verification.isVerifiedUsingV1Scheme)
        assertTrue("Missing v2 signature", verification.isVerifiedUsingV2Scheme)

        val badging = ProcessBuilder(resolved.aapt2.absolutePath, "dump", "badging", signed.absolutePath)
            .redirectErrorStream(true).start()
        val badgingOutput = badging.inputStream.bufferedReader().readText()
        assertTrue("badging failed: $badgingOutput", badging.waitFor() == 0)
        assertTrue(badgingOutput.contains("package: name='com.example.iconpack'"))
        assertTrue(badgingOutput.contains("versionCode='1'"))

        val manifest = ProcessBuilder(
            resolved.aapt2.absolutePath,
            "dump",
            "xmltree",
            signed.absolutePath,
            "--file",
            "AndroidManifest.xml",
        ).redirectErrorStream(true).start()
        val manifestOutput = manifest.inputStream.bufferedReader().readText()
        assertTrue("manifest dump failed: $manifestOutput", manifest.waitFor() == 0)
        assertTrue(manifestOutput.contains("hasCode") && manifestOutput.contains("=false"))

        val resources = ProcessBuilder(resolved.aapt2.absolutePath, "dump", "resources", unsigned.absolutePath)
            .redirectErrorStream(true)
            .start()
        val resourcesOutput = resources.inputStream.bufferedReader().readText()
        assertTrue("aapt2 dump resources failed: $resourcesOutput", resources.waitFor() == 0)
        assertTrue(resourcesOutput.contains("drawable/i12345678"))

        workDir.deleteRecursively()
    }

    private fun validPngBytes(): ByteArray {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFF000000.toInt())
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }
}
