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
    fun generateApkDrawableNameUsesPackageReadableName() {
        val first = IconMappingBridge.generateApkDrawableName("com.tencent.mm", mutableSetOf())
        val second = IconMappingBridge.generateApkDrawableName("com.tencent.mm", mutableSetOf())
        assertEquals("com_tencent_mm", first)
        assertEquals(first, second)
        assertTrue(IconMappingBridge.isPackageDerivedDrawableName(first, "com.tencent.mm"))
        assertFalse(IconMappingBridge.isLegacyHashedDrawableName(first))
        assertTrue(IconMappingBridge.isLegacyHashedDrawableName("i3a5f2b1c"))
    }

    @Test
    fun buildAppfilterXmlDeclaresMaskLayers() {
        val xml = IconMappingBridge.buildAppfilterXml(
            mappings = emptyList(),
            maskLayerDrawables = listOf("iconback", "iconmask", "iconupon"),
        )
        assertTrue(xml.contains("<iconback"))
        assertTrue(xml.contains("""img1="iconback""""))
        assertTrue(xml.contains("<iconmask"))
        assertTrue(xml.contains("""img1="iconmask""""))
        assertTrue(xml.contains("<iconupon"))
        assertTrue(xml.contains("""img1="iconupon""""))
        assertTrue(xml.contains("<scale"))
        assertTrue(xml.contains("""factor="1""""))
        assertFalse(xml.contains("""component="iconback""""))
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
    fun preferIncomingMappingsKeepsApkDrawableNames() {
        val existing = com.bocchi.iconeditor.model.IconMappingIndex(
            entries = listOf(
                com.bocchi.iconeditor.model.IconMappingEntry(
                    packageName = "com.example.app",
                    drawableName = "",
                    components = listOf("ComponentInfo{com.example.app/com.example.app.Main}"),
                ),
                com.bocchi.iconeditor.model.IconMappingEntry(
                    packageName = "com.keep.me",
                    drawableName = "keep_drawable",
                    components = listOf("ComponentInfo{com.keep.me/com.keep.me.Main}"),
                ),
            ),
        )
        val incoming = com.bocchi.iconeditor.model.IconMappingIndex(
            entries = listOf(
                com.bocchi.iconeditor.model.IconMappingEntry(
                    packageName = "com.example.app",
                    drawableName = "iabcdef12",
                    components = listOf("ComponentInfo{com.example.app/com.example.app.Launcher}"),
                ),
            ),
        )
        val merged = IconMappingBridge.preferIncomingMappings(
            existing = existing,
            incoming = incoming,
            packages = setOf("com.example.app"),
        )
        val app = merged.entries.first { it.packageName == "com.example.app" }
        val keep = merged.entries.first { it.packageName == "com.keep.me" }
        assertEquals("iabcdef12", app.drawableName)
        assertEquals("ComponentInfo{com.example.app/com.example.app.Launcher}", app.components.single())
        assertEquals("keep_drawable", keep.drawableName)
    }

    @Test
    fun normalizeAliasPackageNamesDropsPrimaryAndDuplicates() {
        val aliases = IconMappingBridge.normalizeAliasPackageNames(
            listOf(" com.a ", "com.b", "com.a", "com.primary"),
            primary = "com.primary",
        )
        assertEquals(listOf("com.a", "com.b"), aliases)
    }

    @Test
    fun buildAppfilterXmlIncludesAliasFallbackComponents() {
        val xml = IconMappingBridge.buildAppfilterXml(
            mappings = listOf(
                com.bocchi.iconeditor.model.IconMappingEntry(
                    packageName = "com.primary",
                    drawableName = "icon_primary",
                    components = listOf("ComponentInfo{com.primary/com.primary.Main}"),
                    aliasPackageNames = listOf("com.alias"),
                ),
            ),
        )
        assertTrue(xml.contains("""component="ComponentInfo{com.primary/com.primary.Main}""""))
        assertTrue(xml.contains("""component="ComponentInfo{com.alias/com.alias}""""))
        assertTrue(xml.contains("""drawable="icon_primary""""))
    }
}
