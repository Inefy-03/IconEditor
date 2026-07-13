package com.bocchi.iconeditor

import com.bocchi.iconeditor.data.ArchiveService
import com.bocchi.iconeditor.model.ExportFormat
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.model.SourceType
import com.bocchi.iconeditor.model.ProjectSummary
import com.bocchi.iconeditor.ui.ProjectImportMimeTypes
import com.bocchi.iconeditor.ui.ProjectImportPrimaryMimeType
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ArchiveServiceTest {
    @Test
    fun importPickerDoesNotExposeGenericFiles() {
        assertEquals("application/zip", ProjectImportPrimaryMimeType)
        assertTrue("application/zip" in ProjectImportMimeTypes)
        assertTrue("application/x-miui-theme" in ProjectImportMimeTypes)
        assertFalse("application/octet-stream" in ProjectImportMimeTypes)
        assertFalse("*/*" in ProjectImportMimeTypes)
    }

    @Test
    fun requiresTopLevelIconsFileForModuleImport() {
        val temp = Files.createTempDirectory("iconeditor-import-validation").toFile()
        val valid = File(temp, "valid.zip").apply {
            writeBytes(zipBytes { entry("icons", byteArrayOf()) })
        }
        val invalid = File(temp, "invalid.zip").apply {
            writeBytes(zipBytes { entry("module.prop", "id=test") })
        }

        assertTrue(ArchiveService.hasTopLevelIconsEntry(valid))
        assertFalse(ArchiveService.hasTopLevelIconsEntry(invalid))
    }

    @Test
    fun readsLegacyModuleFieldNames() {
        val metadata = Json.decodeFromString<ProjectMetadata>("""{"magisk":{"id":"legacy"}}""")
        val project = Json.decodeFromString<ProjectSummary>(
            """{"id":"p","name":"Legacy","sourceType":"Magisk"}""",
        )

        assertEquals("legacy", metadata.module.id)
        assertEquals(SourceType.Module, project.sourceType)
        assertFalse(Json.encodeToString(metadata).contains("magisk"))
        assertTrue(Json.encodeToString(project).contains("\"sourceType\":\"Module\""))
    }

    @Test
    fun normalizesSelectedVariantAndRenumbersRemainingFiles() {
        val work = Files.createTempDirectory("iconeditor-variants").toFile()
        val iconRoot = File(work, "icons/res/drawable-xxhdpi").apply { mkdirs() }
        File(iconRoot, "com.example.app.png").writeBytes(byteArrayOf(1))
        File(iconRoot, "com.example.app_2.webp").writeBytes(byteArrayOf(2))
        File(iconRoot, "com.example.app_7.png").writeBytes(byteArrayOf(3))
        val previousLastModified = iconRoot.listFiles().orEmpty().maxOf { it.lastModified() }

        val selectedKey = ArchiveService.normalizeIconVariants(
            workDir = work,
            packageName = "com.example.app",
            selectedVariantKey = "com.example.app_7",
        )

        assertEquals("com.example.app", selectedKey)
        assertEquals(byteArrayOf(3).toList(), File(iconRoot, "com.example.app.png").readBytes().toList())
        assertEquals(byteArrayOf(1).toList(), File(iconRoot, "com.example.app_1.png").readBytes().toList())
        assertEquals(byteArrayOf(2).toList(), File(iconRoot, "com.example.app_2.webp").readBytes().toList())
        assertFalse(File(iconRoot, "com.example.app_7.png").exists())
        assertTrue(File(iconRoot, "com.example.app.png").lastModified() > previousLastModified)
    }

    @Test
    fun importsAndExportsMtzMetadataAndIcons() {
        val temp = Files.createTempDirectory("iconeditor-mtz").toFile()
        val source = File(temp, "theme.mtz")
        source.writeBytes(zipBytes {
            entry("description.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <theme>
                  <version><![CDATA[1.0]]></version>
                  <author><![CDATA[A]]></author>
                  <designer><![CDATA[D]]></designer>
                  <title><![CDATA[T]]></title>
                  <description><![CDATA[Desc]]></description>
                </theme>
            """.trimIndent())
            entry("icons", zipBytes {
                entry("transform_config.xml", "<transform_config />")
                entry("res/drawable-xxhdpi/com.example.app.png", byteArrayOf(1, 2, 3))
            })
            entry("import-only.txt", "must not be exported")
        })

        val work = File(temp, "work")
        val sourceExtract = File(temp, "source")
        val result = ArchiveService.importArchive(source, SourceType.Mtz, work, sourceExtract)

        assertEquals("1.0", result.metadata.mtz.version)
        assertEquals("T", result.metadata.mtz.title)
        assertTrue(File(work, "icons/res/drawable-xxhdpi/com.example.app.png").exists())

        val out = ByteArrayOutputStream()
        ArchiveService.exportArchive(
            metadata = result.metadata.copy(
                mtz = result.metadata.mtz.copy(version = "2.0", uiVersion = "99"),
            ),
            workDir = work,
            format = ExportFormat.Mtz,
            templateFiles = mapOf("com.miui.home" to "test".encodeToByteArray()),
            iconsTemplate = nestedIconsTemplate("<template />"),
            sourceArchive = source,
            output = out,
        )
        val exported = unzipEntries(out.toByteArray())
        val description = exported.getValue("description.xml").decodeToString()
        val exportedIcons = unzipEntries(exported.getValue("icons"))
        assertTrue(description.contains("2.0"))
        assertTrue(description.contains("<![CDATA[17]]>"))
        assertEquals(2, description.windowed("<![CDATA[A]]>".length).count { it == "<![CDATA[A]]>" })
        assertEquals("must not be exported", exported.getValue("import-only.txt").decodeToString())
        assertEquals("<transform_config />", exportedIcons.getValue("transform_config.xml").decodeToString())
        assertEquals(ZipEntry.STORED, zipMethods(out.toByteArray()).getValue("icons"))
    }

    @Test
    fun importsAndExportsModuleMetadataAndCustomizeMessages() {
        val temp = Files.createTempDirectory("iconeditor-module").toFile()
        val source = File(temp, "module.zip")
        source.writeBytes(zipBytes {
            entry("module.prop", """
                id=GlossyIcon
                name=Glossy Icon Project
                author=Bocchi
                description=Icons
                version=26.6.2
                theme=GlossyIcon
                themeid=glossyicon
            """.trimIndent())
            entry("customize.sh", """
                #!/sbin/sh
                ui_print "hello"
                SKIPUNZIP=1
            """.trimIndent())
            entry("post-fs-data.sh", "#!/sbin/sh")
            entry("icons", zipBytes {
                entry("res/drawable-xxhdpi/com.example.app_1.png", byteArrayOf(1, 2, 3))
            })
            entry("import-only.txt", "must not be exported")
        })

        val work = File(temp, "work")
        val sourceExtract = File(temp, "source")
        val result = ArchiveService.importArchive(source, SourceType.Module, work, sourceExtract)

        assertEquals("GlossyIcon", result.metadata.module.id)
        assertEquals(listOf("hello"), result.metadata.module.installMessages)
        assertTrue(File(work, "icons/res/drawable-xxhdpi/com.example.app_1.png").exists())

        val out = ByteArrayOutputStream()
        val templateFiles = linkedMapOf(
            "META-INF/com/google/android/update-binary" to "update-binary".encodeToByteArray(),
            "META-INF/com/google/android/updater-script" to "#MODULE\n".encodeToByteArray(),
            "customize.sh" to "#!/sbin/sh\nui_print \"template\"\nSKIPUNZIP=1\n".encodeToByteArray(),
            "post-fs-data.sh" to "post-fs-template".encodeToByteArray(),
        )
        ArchiveService.exportArchive(
            metadata = result.metadata.copy(
                module = result.metadata.module.copy(installMessages = listOf("changed", "second line")),
            ),
            workDir = work,
            format = ExportFormat.ModuleZip,
            templateFiles = templateFiles,
            iconsTemplate = nestedIconsTemplate("<template />"),
            sourceArchive = source,
            output = out,
        )
        val exported = unzipEntries(out.toByteArray())
        val customize = exported.getValue("customize.sh").decodeToString()
        assertTrue(exported.getValue("module.prop").decodeToString().contains("themeid=glossyicon"))
        assertEquals(
            listOf("ui_print \"changed\"", "ui_print \"second line\""),
            customize.lines().filter { it.startsWith("ui_print ") },
        )
        assertTrue(customize.contains("SKIPUNZIP=1"))
        assertEquals("#!/sbin/sh", exported.getValue("post-fs-data.sh").decodeToString())
        assertEquals("must not be exported", exported.getValue("import-only.txt").decodeToString())
        assertEquals(ZipEntry.STORED, zipMethods(out.toByteArray()).getValue("icons"))
    }

    @Test
    fun templateExportUsesSuppliedNestedIconsArchive() {
        val temp = Files.createTempDirectory("iconeditor-icons-template").toFile()
        val work = File(temp, "work")
        ArchiveService.createDefaultWorkspace(work)
        File(work, "icons/res/drawable-xxhdpi/com.example.app.png").writeBytes(byteArrayOf(1, 2, 3))
        val out = ByteArrayOutputStream()

        ArchiveService.exportArchive(
            metadata = ProjectMetadata(),
            workDir = work,
            format = ExportFormat.Mtz,
            templateFiles = mapOf("com.miui.home" to "test".encodeToByteArray()),
            iconsTemplate = nestedIconsTemplate("<xiaomi-template />"),
            output = out,
        )

        val outer = unzipEntries(out.toByteArray())
        val icons = unzipEntries(outer.getValue("icons"))
        assertEquals("<xiaomi-template />", icons.getValue("transform_config.xml").decodeToString())
        assertEquals(
            byteArrayOf(1, 2, 3).toList(),
            icons.getValue("res/drawable-xxhdpi/com.example.app.png").toList(),
        )
    }

    @Test
    fun streamsLargeEntriesDuringSameFormatExport() {
        val temp = Files.createTempDirectory("iconeditor-stream-export").toFile()
        val largeContent = File(temp, "large-content.bin").apply {
            outputStream().buffered().use { output ->
                val block = ByteArray(8192) { index -> index.toByte() }
                repeat(1024) { output.write(block) }
            }
        }
        val sourceIcons = File(temp, "source-icons.zip").apply {
            ZipOutputStream(outputStream().buffered()).use { zip ->
                zip.entry("layer_animating_icons/large-content.bin", largeContent)
            }
        }
        val source = File(temp, "source.mtz").apply {
            ZipOutputStream(outputStream().buffered()).use { zip ->
                zip.entry("description.xml", "<theme />")
                zip.entry("large-preserved.bin", largeContent)
                zip.entry("icons", sourceIcons)
            }
        }
        val work = File(temp, "work")
        ArchiveService.createDefaultWorkspace(work)
        val exported = File(temp, "exported.mtz")

        exported.outputStream().buffered().use { output ->
            ArchiveService.exportArchive(
                metadata = ProjectMetadata(),
                workDir = work,
                format = ExportFormat.Mtz,
                templateFiles = mapOf("com.miui.home" to "test".encodeToByteArray()),
                iconsTemplate = nestedIconsTemplate("<template />"),
                sourceArchive = source,
                output = output,
            )
        }

        ZipFile(source).use { original ->
            ZipFile(exported).use { result ->
                assertEquals(
                    original.getEntry("large-preserved.bin").crc,
                    result.getEntry("large-preserved.bin").crc,
                )
                assertEquals(ZipEntry.STORED, result.getEntry("icons").method)
                val rebuiltIcons = File(temp, "rebuilt-icons.zip")
                result.getInputStream(result.getEntry("icons")).use { input ->
                    rebuiltIcons.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                ZipFile(sourceIcons).use { originalIcons ->
                    ZipFile(rebuiltIcons).use { resultIcons ->
                        assertEquals(
                            originalIcons.getEntry("layer_animating_icons/large-content.bin").crc,
                            resultIcons.getEntry("layer_animating_icons/large-content.bin").crc,
                        )
                    }
                }
            }
        }
    }

    private fun zipBytes(builder: ZipOutputStream.() -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.builder() }
        return out.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        entry(name, content.encodeToByteArray())
    }

    private fun ZipOutputStream.entry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }

    private fun ZipOutputStream.entry(name: String, content: File) {
        putNextEntry(ZipEntry(name))
        content.inputStream().buffered().use { it.copyTo(this) }
        closeEntry()
    }

    private fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun zipMethods(bytes: ByteArray): Map<String, Int> {
        val file = Files.createTempFile("iconeditor-zip-methods", ".zip").toFile()
        file.writeBytes(bytes)
        return ZipFile(file).use { zip ->
            zip.entries().asSequence().associate { it.name to it.method }
        }.also { file.delete() }
    }

    private fun nestedIconsTemplate(transformConfig: String): ByteArray = zipBytes {
        entry("transform_config.xml", transformConfig)
    }
}
