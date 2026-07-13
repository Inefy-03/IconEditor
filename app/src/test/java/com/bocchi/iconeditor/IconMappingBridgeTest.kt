package com.bocchi.iconeditor

import com.bocchi.iconeditor.data.IconMappingBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IconMappingBridgeTest {
    @Test
    fun isValidAndroidPackageNameRequiresDottedIdentifier() {
        assertTrue(IconMappingBridge.isValidAndroidPackageName("com.example.app"))
        assertFalse(IconMappingBridge.isValidAndroidPackageName("browser"))
        assertFalse(IconMappingBridge.isValidAndroidPackageName(""))
    }

    @Test
    fun packageToDrawableNameReplacesDots() {
        assertEquals("com_tencent_mm", IconMappingBridge.packageToDrawableName("com.tencent.mm"))
    }

    @Test
    fun inferPackageNameUsesPlainMtzFileName() {
        assertEquals("com.example.app", IconMappingBridge.inferPackageName("com.example.app"))
        assertEquals("com.example.app", IconMappingBridge.inferPackageName("com.example.app_2"))
    }

    @Test
    fun generateApkDrawableNameIsStableAndNotPackageDerived() {
        val first = IconMappingBridge.generateApkDrawableName("com.tencent.mm", mutableSetOf())
        val second = IconMappingBridge.generateApkDrawableName("com.tencent.mm", mutableSetOf())
        assertEquals(first, second)
        assertFalse(IconMappingBridge.isPackageDerivedDrawableName(first, "com.tencent.mm"))
        assertTrue(first.matches(Regex("i[0-9a-f]{8}")))
    }

    @Test
    fun resolveIconFileFindsPlainPackageNamedPng() {
        val workDir = java.nio.file.Files.createTempDirectory("iconeditor-apk-export").toFile()
        val iconRoot = File(workDir, "icons/res/drawable-xxhdpi").apply { mkdirs() }
        File(iconRoot, "com.tencent.mm.png").writeBytes(byteArrayOf(1, 2, 3))
        val entry = com.bocchi.iconeditor.model.IconMappingEntry(
            packageName = "com.tencent.mm",
            drawableName = "i3a5f2b1c",
            components = listOf("ComponentInfo{com.tencent.mm/com.tencent.mm.MainActivity}"),
        )
        val icons = listOf(
            com.bocchi.iconeditor.model.IconAsset(
                packageName = "com.tencent.mm",
                variantKey = "com.tencent.mm",
                archivePath = "icons/res/drawable-xxhdpi/com.tencent.mm.png",
                fileName = "com.tencent.mm.png",
            ),
        )
        val resolved = IconMappingBridge.resolveIconFile(workDir, entry, icons)
        assertEquals(byteArrayOf(1, 2, 3).toList(), resolved?.readBytes()?.toList())
        workDir.deleteRecursively()
    }

    @Test
    fun parseComponentInfoSupportsShortActivityName() {
        val parsed = IconMappingBridge.parseComponentInfo(
            "ComponentInfo{com.android.deskclock/.DeskClockTabActivity}",
        )
        assertEquals("com.android.deskclock" to "ComponentInfo{com.android.deskclock/com.android.deskclock.DeskClockTabActivity}", parsed)
    }

    @Test
    fun dedupeMappingsPicksMostCommonDrawable() {
        val items = IconMappingBridge.parseAppfilterXml(
            """
            <resources>
              <item component="ComponentInfo{a/a.A}" drawable="icon_a" />
              <item component="ComponentInfo{a/a.B}" drawable="icon_a" />
              <item component="ComponentInfo{a/a.C}" drawable="icon_b" />
            </resources>
            """.trimIndent(),
        )
        val deduped = IconMappingBridge.dedupeMappings(items)
        assertEquals(1, deduped.size)
        assertEquals("a", deduped.first().packageName)
        assertEquals("icon_a", deduped.first().drawableName)
        assertEquals(3, deduped.first().components.size)
    }

    @Test
    fun buildAppfilterXmlContainsMappings() {
        val xml = IconMappingBridge.buildAppfilterXml(
            listOf(
                com.bocchi.iconeditor.model.IconMappingEntry(
                    packageName = "com.example.app",
                    drawableName = "i9f3a2b1c",
                    components = listOf("ComponentInfo{com.example.app/com.example.app.MainActivity}"),
                ),
            ),
        )
        assertTrue(xml.contains("drawable=\"i9f3a2b1c\""))
        assertTrue(xml.contains("ComponentInfo{com.example.app/com.example.app.MainActivity}"))
    }
}
