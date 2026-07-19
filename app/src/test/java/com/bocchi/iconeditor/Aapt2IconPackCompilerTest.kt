package com.bocchi.iconeditor

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
import javax.imageio.ImageIO

class Aapt2IconPackCompilerTest {
    @Test
    fun buildUnsignedApkWithAapt2() {
        val toolchain = Aapt2Toolchain.resolveHostToolchain()
        assumeTrue("需要本机 ANDROID_HOME aapt2", toolchain != null)
        val resolved = requireNotNull(toolchain)

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

        val projectRoot = File(checkNotNull(System.getProperty("user.dir")))
        val ks = File(projectRoot, "app/src/main/assets/archive_templates/apk/export_keystore.jks")
        val signer = File(resolved.aapt2.parentFile, "apksigner")
        if (signer.isFile && ks.isFile) {
            val sign = ProcessBuilder(
                signer.absolutePath,
                "sign",
                "--ks", ks.absolutePath,
                "--ks-pass", "pass:iconeditor",
                "--key-pass", "pass:iconeditor",
                "--ks-key-alias", "iconeditor_export",
                "--out", signed.absolutePath,
                unsigned.absolutePath,
            ).redirectErrorStream(true).start()
            val signOutput = sign.inputStream.bufferedReader().readText()
            assertTrue("apksigner failed: $signOutput", sign.waitFor() == 0)

            val badging = ProcessBuilder(resolved.aapt2.absolutePath, "dump", "badging", signed.absolutePath)
                .redirectErrorStream(true).start()
            val badgingOutput = badging.inputStream.bufferedReader().readText()
            assertTrue("badging failed: $badgingOutput", badging.waitFor() == 0)
            assertTrue(badgingOutput.contains("package: name='com.example.iconpack'"))
            assertTrue(badgingOutput.contains("versionCode='1'"))
            assertTrue(
                badgingOutput.contains("hasCode='false'") ||
                    badgingOutput.contains("hasCode: 'false'"),
            )
        }

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
