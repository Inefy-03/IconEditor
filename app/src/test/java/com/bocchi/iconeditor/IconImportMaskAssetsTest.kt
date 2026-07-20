package com.bocchi.iconeditor

import com.bocchi.iconeditor.data.ApkPackAssets
import com.bocchi.iconeditor.data.ThemePackAssets
import com.bocchi.iconeditor.data.copyImportedMaskAssets
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconImportMaskAssetsTest {
    @Test
    fun copiesApkAndThemeMasksAndReconcilesTransformConfig() {
        val root = Files.createTempDirectory("icon-import-masks").toFile()
        try {
            val source = File(root, "source")
            val target = File(root, "target")
            val expected = linkedMapOf<String, ByteArray>()
            (ApkPackAssets.MaskLayer.entries.map { it.relativePath } +
                ThemePackAssets.MtzMaskLayer.entries.map { it.relativePath })
                .forEachIndexed { index, relativePath ->
                    val bytes = byteArrayOf(index.toByte(), (index + 1).toByte())
                    File(source, relativePath).apply {
                        parentFile?.mkdirs()
                        writeBytes(bytes)
                    }
                    expected[relativePath] = bytes
                }

            File(target, ApkPackAssets.LEGACY_MASK_PATH).apply {
                parentFile?.mkdirs()
                writeText("legacy")
            }
            File(target, ThemePackAssets.TRANSFORM_CONFIG_PATH).apply {
                parentFile?.mkdirs()
                writeText(
                    """<?xml version="1.0" encoding="UTF-8"?>
                        |<IconTransform>
                        |    <Config name="KeepMe" value="keep" />
                        |    <Config name="ConfigIconMask" value="old" />
                        |</IconTransform>
                    """.trimMargin(),
                )
            }

            copyImportedMaskAssets(source, target)

            expected.forEach { (relativePath, bytes) ->
                assertArrayEquals(bytes, File(target, relativePath).readBytes())
            }
            assertFalse(File(target, ApkPackAssets.LEGACY_MASK_PATH).exists())
            val transform = File(target, ThemePackAssets.TRANSFORM_CONFIG_PATH).readText()
            assertTrue(transform.contains("KeepMe"))
            assertFalse(transform.contains("ConfigIconMask"))
        } finally {
            root.deleteRecursively()
        }
    }
}
