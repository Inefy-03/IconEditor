package com.bocchi.iconeditor.data

import android.graphics.BitmapFactory
import com.bocchi.iconeditor.model.ExportFormat
import com.bocchi.iconeditor.model.ExportPhase
import com.bocchi.iconeditor.model.IconAsset
import com.bocchi.iconeditor.model.ModuleInfo
import com.bocchi.iconeditor.model.MtzInfo
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.model.SourceType
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class ImportResult(val metadata: ProjectMetadata)
class InvalidArchivePathException(val entryName: String) : IllegalArgumentException()
class InvalidProjectArchiveException : IllegalArgumentException()

object ArchiveService {
    private val imageExtensions = setOf("png", "webp", "jpg", "jpeg", "svg")

    fun hasTopLevelIconsEntry(source: File): Boolean = runCatching {
        ZipFile(source).use { zip -> zip.getEntry("icons")?.isDirectory == false }
    }.getOrDefault(false)

    fun importArchive(source: File, sourceType: SourceType, workDir: File, sourceExtractDir: File): ImportResult {
        workDir.deleteRecursively()
        sourceExtractDir.deleteRecursively()
        workDir.mkdirs()
        sourceExtractDir.mkdirs()

        when (sourceType) {
            SourceType.Mtz, SourceType.Module -> {
                unzip(source.inputStream(), workDir)
                normalizeNestedIcons(workDir)
                copyDirectory(workDir, sourceExtractDir)
            }
            SourceType.Universal -> {
                createDefaultWorkspace(workDir)
                copyDirectory(workDir, sourceExtractDir)
            }
            SourceType.Apk -> error("APK import is handled by ApkIconPackImporter")
        }

        val metadata = when (sourceType) {
            SourceType.Mtz -> parseMtzMetadata(workDir).let { mtz ->
                ProjectMetadata(
                    mtz = mtz,
                    module = ModuleInfo(
                        id = mtz.title.filter { it.isLetterOrDigit() }.ifBlank { "IconModule" },
                        name = mtz.title,
                        author = mtz.author,
                        description = mtz.description.lines().firstOrNull().orEmpty(),
                        version = mtz.version,
                        theme = mtz.title.filter { it.isLetterOrDigit() }.ifBlank { mtz.title },
                        themeId = mtz.title.lowercase().filter { it.isLetterOrDigit() },
                    ),
                )
            }
            SourceType.Module -> parseModuleMetadata(workDir).let { module ->
                ProjectMetadata(
                    mtz = MtzInfo(
                        version = module.version,
                        author = module.author,
                        designer = module.author,
                        title = module.name,
                        description = module.description,
                    ),
                    module = module,
                )
            }
            SourceType.Universal -> ProjectMetadata()
            SourceType.Apk -> error("APK import is handled by ApkIconPackImporter")
        }
        return ImportResult(metadata)
    }

    fun createDefaultWorkspace(workDir: File) {
        File(workDir, "icons/res/drawable-xxhdpi").mkdirs()
        File(workDir, "icons/transform_config.xml").writeText("<transform_config />\n")
        File(workDir, "customize.sh").writeText(defaultCustomize(emptyList()))
        File(workDir, "post-fs-data.sh").writeText("#!/sbin/sh\n")
    }

    fun scanIconAssets(workDir: File): List<IconAsset> {
        val iconRoot = File(workDir, "icons/res/drawable-xxhdpi")
        if (!iconRoot.exists()) return emptyList()
        return iconRoot.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in imageExtensions }
            .map { file ->
                val relative = file.relativeTo(workDir).invariantSeparatorsPath
                val name = file.nameWithoutExtension
                val packageName = name.removeVariantSuffix()
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (file.extension.lowercase() != "svg") {
                    BitmapFactory.decodeFile(file.absolutePath, options)
                }
                IconAsset(
                    packageName = packageName,
                    variantKey = name,
                    archivePath = relative,
                    fileName = file.name,
                    width = options.outWidth.coerceAtLeast(0),
                    height = options.outHeight.coerceAtLeast(0),
                    lastModified = file.lastModified(),
                )
            }
            .filter { it.packageName !in ApkPackAssets.MaskLayer.resourceNames }
            .sortedBy { it.packageName }
            .toList()
    }

    /** Fast package scan for sync inventory — size/mtime only, no image decode. */
    fun scanIconAssetsLite(workDir: File): List<IconAsset> {
        val iconRoot = File(workDir, "icons/res/drawable-xxhdpi")
        if (!iconRoot.exists()) return emptyList()
        return iconRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.lowercase() in imageExtensions }
            .map { file ->
                val name = file.nameWithoutExtension
                val packageName = name.removeVariantSuffix()
                IconAsset(
                    packageName = packageName,
                    variantKey = name,
                    archivePath = file.relativeTo(workDir).invariantSeparatorsPath,
                    fileName = file.name,
                    width = 0,
                    height = 0,
                    lastModified = file.lastModified(),
                )
            }
            .filter { it.packageName !in ApkPackAssets.MaskLayer.resourceNames }
            .sortedBy { it.packageName }
            .toList()
    }

    /** Fast path for sync: list files for one package without decoding image bounds. */
    fun listIconFiles(workDir: File, packageName: String): List<File> {
        if (packageName.isBlank() || packageName in ApkPackAssets.MaskLayer.resourceNames) return emptyList()
        val iconRoot = File(workDir, "icons/res/drawable-xxhdpi")
        if (!iconRoot.isDirectory) return emptyList()
        return iconRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.lowercase() in imageExtensions }
            .filter { it.nameWithoutExtension.removeVariantSuffix() == packageName }
            .sortedWith(
                compareBy<File> { variantOrder(it.nameWithoutExtension, packageName) }
                    .thenBy { it.name },
            )
            .toList()
    }

    fun deleteIconFiles(workDir: File, packageName: String) {
        listIconFiles(workDir, packageName).forEach { it.delete() }
    }

    fun normalizeIconVariants(
        workDir: File,
        packageName: String,
        selectedVariantKey: String?,
    ): String? {
        val iconRoot = File(workDir, "icons/res/drawable-xxhdpi")
        val variants = iconRoot.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.extension.lowercase() in imageExtensions &&
                    file.nameWithoutExtension.removeVariantSuffix() == packageName
            }
            .sortedWith(
                compareBy<File> { variantOrder(it.nameWithoutExtension, packageName) }
                    .thenBy { it.name },
            )
        if (variants.isEmpty()) return null

        val selected = variants.firstOrNull { it.nameWithoutExtension == selectedVariantKey }
            ?: variants.first()
        val refreshedAt = maxOf(
            System.currentTimeMillis(),
            variants.maxOf(File::lastModified) + 1_000L,
        )
        val ordered = listOf(selected) + variants.filterNot { it == selected }
        val operationId = UUID.randomUUID().toString()
        val moves = ordered.mapIndexed { index, source ->
            val suffix = if (index == 0) "" else "_$index"
            VariantMove(
                source = source,
                staged = File(iconRoot, ".iconeditor-$operationId-$index.tmp"),
                target = File(iconRoot, "$packageName$suffix.${source.extension}"),
            )
        }

        try {
            moves.forEach { moveFile(it.source, it.staged) }
        } catch (error: Throwable) {
            moves.asReversed().forEach { move ->
                if (move.staged.exists()) moveFile(move.staged, move.source)
            }
            throw error
        }

        try {
            moves.forEach { moveFile(it.staged, it.target) }
            moves.forEachIndexed { index, move ->
                Files.setLastModifiedTime(
                    move.target.toPath(),
                    FileTime.fromMillis(refreshedAt + index),
                )
            }
        } catch (error: Throwable) {
            moves.forEach { move ->
                if (move.target.exists() && !move.staged.exists()) {
                    moveFile(move.target, move.staged)
                }
            }
            moves.forEach { move ->
                if (move.staged.exists()) moveFile(move.staged, move.source)
            }
            throw error
        }
        return packageName
    }

    fun renameIconPackage(workDir: File, from: String, to: String) {
        val oldPackage = from.trim()
        val newPackage = to.trim()
        if (oldPackage == newPackage) return
        require(oldPackage.isNotEmpty() && newPackage.isNotEmpty()) { "包名不能为空" }
        val assets = scanIconAssets(workDir)
        require(assets.none { it.packageName == newPackage }) { "包名已存在：$newPackage" }
        val toRename = assets.filter { it.packageName == oldPackage }
            .sortedWith(
                compareBy<IconAsset> { variantOrder(it.variantKey, oldPackage) }
                    .thenBy { it.fileName },
            )
        if (toRename.isEmpty()) return
        val iconRoot = File(workDir, "icons/res/drawable-xxhdpi")
        val operationId = UUID.randomUUID().toString()
        data class RenameMove(val source: File, val staged: File, val target: File)
        val moves = toRename.mapIndexed { index, asset ->
            val source = File(workDir, asset.archivePath)
            val suffix = if (index == 0) "" else "_$index"
            RenameMove(
                source = source,
                staged = File(iconRoot, ".iconeditor-rename-$operationId-$index.tmp"),
                target = File(iconRoot, "$newPackage$suffix.${source.extension}"),
            )
        }
        try {
            moves.forEach { moveFile(it.source, it.staged) }
        } catch (error: Throwable) {
            moves.asReversed().forEach { move ->
                if (move.staged.exists()) moveFile(move.staged, move.source)
            }
            throw error
        }
        try {
            moves.forEach { move ->
                if (move.target.exists()) move.target.delete()
                moveFile(move.staged, move.target)
            }
        } catch (error: Throwable) {
            moves.forEach { move ->
                if (move.target.exists() && !move.staged.exists()) {
                    moveFile(move.target, move.staged)
                }
            }
            moves.forEach { move ->
                if (move.staged.exists()) moveFile(move.staged, move.source)
            }
            throw error
        }
    }

    fun exportArchive(
        metadata: ProjectMetadata,
        workDir: File,
        format: ExportFormat,
        templateFiles: Map<String, ByteArray>,
        iconsTemplate: ByteArray,
        sourceArchive: File? = null,
        output: OutputStream,
        reporter: ExportProgressReporter = ExportProgressReporter.NOOP,
    ) {
        when (format) {
            ExportFormat.Mtz -> exportMtz(
                metadata.mtz,
                workDir,
                templateFiles,
                iconsTemplate,
                sourceArchive,
                output,
                reporter,
            )
            ExportFormat.ModuleZip -> exportModule(
                metadata.module,
                workDir,
                templateFiles,
                iconsTemplate,
                sourceArchive,
                output,
                reporter,
            )
            ExportFormat.Apk -> error("APK export is handled by ApkIconPackExporter")
        }
    }

    private fun exportMtz(
        info: MtzInfo,
        workDir: File,
        templateFiles: Map<String, ByteArray>,
        iconsTemplate: ByteArray,
        sourceArchive: File?,
        output: OutputStream,
        reporter: ExportProgressReporter,
    ) {
        reporter.log("写入 MTZ description.xml")
        exportFromBase(
            output = output,
            sourceArchive = sourceArchive,
            templateFiles = templateFiles,
            replacedEntries = setOf("description.xml", "icons"),
            reporter = reporter,
        ) { zip, source ->
            zip.writeEntry("description.xml", buildMtzDescription(info))
            zip.writeIconsArchive(workDir, source, iconsTemplate, reporter)
        }
    }

    private fun exportModule(
        info: ModuleInfo,
        workDir: File,
        templateFiles: Map<String, ByteArray>,
        iconsTemplate: ByteArray,
        sourceArchive: File?,
        output: OutputStream,
        reporter: ExportProgressReporter,
    ) {
        reporter.log("写入 Module 元数据")
        exportFromBase(
            output = output,
            sourceArchive = sourceArchive,
            templateFiles = templateFiles,
            replacedEntries = setOf("customize.sh", "module.prop", "icons"),
            reporter = reporter,
        ) { zip, source ->
            val customizeBase = source?.readEntry("customize.sh")
                ?: templateFiles.getValue("customize.sh")
            zip.writeEntry("customize.sh", mergeCustomize(customizeBase, info.installMessages))
            zip.writeEntry("module.prop", buildModuleProp(info).encodeToByteArray())
            zip.writeIconsArchive(workDir, source, iconsTemplate, reporter)
        }
    }

    private inline fun exportFromBase(
        output: OutputStream,
        sourceArchive: File?,
        templateFiles: Map<String, ByteArray>,
        replacedEntries: Set<String>,
        reporter: ExportProgressReporter,
        writeEditableEntries: (ZipOutputStream, ZipFile?) -> Unit,
    ) {
        if (sourceArchive != null) {
            reporter.log("基于源归档：${sourceArchive.name}")
        } else {
            reporter.log("使用内置模板文件")
        }
        ZipOutputStream(output).use { zip ->
            if (sourceArchive != null) {
                ZipFile(sourceArchive).use { source ->
                    copyUnmodifiedEntries(zip, source, replacedEntries)
                    writeEditableEntries(zip, source)
                }
            } else {
                addTemplateFiles(zip, templateFiles - replacedEntries)
                writeEditableEntries(zip, null)
            }
        }
    }

    private fun normalizeNestedIcons(root: File) {
        val nested = File(root, "icons")
        if (!nested.isFile) {
            if (!nested.exists()) File(root, "icons/res/drawable-xxhdpi").mkdirs()
            return
        }
        val packedIcons = File(root, ".icons-${UUID.randomUUID()}.zip")
        moveFile(nested, packedIcons)
        nested.mkdirs()
        runCatching { packedIcons.inputStream().use { unzip(it, nested) } }
            .onFailure {
                File(nested, "res/drawable-xxhdpi").mkdirs()
            }
        packedIcons.delete()
    }

    private fun parseMtzMetadata(workDir: File): MtzInfo {
        val file = File(workDir, "description.xml")
        if (!file.exists()) return MtzInfo()
        val doc = parseXml(file.inputStream())
        return MtzInfo(
            version = doc.textOf("version"),
            author = doc.textOf("author"),
            designer = doc.textOf("designer"),
            title = doc.textOf("title"),
            description = doc.textOf("description"),
            uiVersion = doc.textOf("uiVersion"),
        )
    }

    private fun parseModuleMetadata(workDir: File): ModuleInfo {
        val prop = File(workDir, "module.prop")
        val values = if (prop.exists()) {
            prop.readLines().mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) null
                else trimmed.substringBefore("=") to trimmed.substringAfter("=")
            }.toMap()
        } else {
            emptyMap()
        }
        return ModuleInfo(
            id = values["id"].orEmpty(),
            name = values["name"].orEmpty(),
            author = values["author"].orEmpty(),
            description = values["description"].orEmpty(),
            version = values["version"].orEmpty(),
            theme = values["theme"].orEmpty(),
            themeId = values["themeid"].orEmpty(),
            installMessages = parseUiPrintMessages(File(workDir, "customize.sh")),
        )
    }

    private fun parseUiPrintMessages(file: File): List<String> {
        if (!file.exists()) return emptyList()
        val regex = Regex("""(?m)^\s*ui_print\s+"((?:\\.|[^"\\])*)"\s*$""")
        return regex.findAll(file.readText())
            .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
            .toList()
    }

    private fun buildMtzDescription(info: MtzInfo): ByteArray {
        val doc = parseXml(MtzDescriptionTemplate.byteInputStream())
        doc.setTexts("version", info.version)
        doc.setTexts("uiVersion", "17")
        doc.setTexts("author", info.author)
        doc.setTexts("designer", info.designer)
        doc.setTexts("title", info.title)
        doc.setTexts("description", info.description)
        return ByteArrayOutputStream().also { stream ->
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            }.transform(DOMSource(doc), StreamResult(stream))
        }.toByteArray()
    }

    private fun buildModuleProp(info: ModuleInfo): String = buildString {
        appendLine("id=${info.id}")
        appendLine("name=${info.name}")
        appendLine("author=${info.author}")
        appendLine("description=${info.description}")
        appendLine("version=${info.version}")
        appendLine("theme=${info.theme}")
        appendLine("themeid=${info.themeId}")
    }

    private fun mergeCustomize(template: ByteArray, messages: List<String>): ByteArray {
        val lines = messages.flatMap(String::lines)
        val replacement = lines.joinToString(separator = "\n", postfix = if (lines.isEmpty()) "" else "\n") {
            "ui_print \"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
        val text = template.decodeToString()
        val merged = if (CustomizeMessages.containsMatchIn(text)) {
            CustomizeMessages.replaceFirst(text, replacement)
        } else {
            text.substringBefore('\n', missingDelimiterValue = text) + "\n" + replacement +
                text.substringAfter('\n', missingDelimiterValue = "")
        }
        return merged.encodeToByteArray()
    }

    private fun defaultCustomize(messages: List<String>): String = buildString {
        appendLine("#!/sbin/sh")
        messages.ifEmpty { listOf("---------------------------------------------", "IconEditor Module", "---------------------------------------------") }
            .forEach { appendLine("""ui_print "$it"""") }
        appendLine()
        appendLine("SKIPUNZIP=1")
        appendLine("""unzip -oj "${'$'}ZIPFILE" icons -d ${'$'}MODPATH/system/media/theme/default/ >&2""")
        appendLine("""unzip -oj "${'$'}ZIPFILE" module.prop -d ${'$'}MODPATH/ >&2""")
        appendLine("set_perm_recursive ${'$'}MODPATH 0 0 0755 0644")
    }

    private fun buildIconsZip(
        workDir: File,
        baseIcons: File,
        output: File,
        reporter: ExportProgressReporter = ExportProgressReporter.NOOP,
    ) {
        ZipFile(baseIcons).use { source ->
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                source.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory || !entry.name.startsWith(EditableIconDirectory)) {
                        zip.copyEntry(source, entry)
                    }
                }
                val editableIcons = File(workDir, "icons/$EditableIconDirectory")
                if (editableIcons.exists()) {
                    val iconFiles = editableIcons.walkTopDown()
                        .filter { it.isFile }
                        .sortedBy { it.relativeTo(editableIcons).invariantSeparatorsPath }
                        .toList()
                    reporter.update(
                        phase = ExportPhase.PackagingIcons,
                        current = 0,
                        total = iconFiles.size,
                        detail = "打包 icons 模块",
                        log = "打包 icons 模块（${iconFiles.size} 个文件）",
                    )
                    iconFiles.forEachIndexed { index, file ->
                        val relative = file.relativeTo(editableIcons).invariantSeparatorsPath
                        zip.writeStoredEntry("$EditableIconDirectory$relative", file)
                        val current = index + 1
                        if (current == 1 || current == iconFiles.size || current % 50 == 0) {
                            reporter.update(
                                phase = ExportPhase.PackagingIcons,
                                current = current,
                                total = iconFiles.size,
                                detail = relative,
                                log = "写入 icons $current/${iconFiles.size}：$relative",
                            )
                        }
                    }
                } else {
                    reporter.log("未找到可编辑图标目录")
                }
            }
        }
    }

    private fun unzip(input: InputStream, targetDir: File) {
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val target = File(targetDir, entry.name).canonicalFile
                if (!target.path.startsWith(targetDir.canonicalPath)) {
                    throw InvalidArchivePathException(entry.name)
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun addDirectoryToZip(zip: ZipOutputStream, root: File, prefix: String = "", exclude: Set<String> = emptySet()) {
        if (!root.exists()) return
        root.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.name in exclude) return@forEach
            val entryName = if (prefix.isBlank()) file.name else "$prefix/${file.name}"
            if (file.isDirectory) {
                zip.putNextEntry(ZipEntry("$entryName/"))
                zip.closeEntry()
                addDirectoryToZip(zip, file, entryName)
            } else {
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun addTemplateFiles(zip: ZipOutputStream, files: Map<String, ByteArray>) {
        files.keys
            .flatMap { name ->
                name.split('/').dropLast(1).runningFold("") { path, part ->
                    if (path.isEmpty()) "$part/" else "$path$part/"
                }.drop(1)
            }
            .distinct()
            .forEach { directory ->
                zip.putNextEntry(ZipEntry(directory))
                zip.closeEntry()
            }
        files.forEach { (name, bytes) -> zip.writeEntry(name, bytes) }
    }

    private fun copyUnmodifiedEntries(
        output: ZipOutputStream,
        source: ZipFile,
        replacedEntries: Set<String>,
    ) {
        source.entries().asSequence().forEach { entry ->
            if (entry.name !in replacedEntries) {
                output.copyEntry(source, entry)
            }
        }
    }

    private fun ZipOutputStream.writeIconsArchive(
        workDir: File,
        source: ZipFile?,
        iconsTemplate: ByteArray,
        reporter: ExportProgressReporter = ExportProgressReporter.NOOP,
    ) {
        val tempDir = Files.createTempDirectory(workDir.toPath(), "export-icons-").toFile()
        try {
            val baseIcons = File(tempDir, "base-icons.zip")
            val sourceEntry = source?.getEntry("icons")
            if (source != null && sourceEntry != null) {
                reporter.log("从源归档读取 icons 模块")
                source.getInputStream(sourceEntry).use { input ->
                    baseIcons.outputStream().buffered().use { output -> input.copyTo(output) }
                }
            } else {
                reporter.log("使用内置 icons 模板")
                baseIcons.writeBytes(iconsTemplate)
            }
            val rebuiltIcons = File(tempDir, "icons.zip")
            buildIconsZip(workDir, baseIcons, rebuiltIcons, reporter)
            reporter.update(phase = ExportPhase.WritingArchive, detail = "写入 icons 条目")
            reporter.log("将 icons 模块写入归档")
            writeStoredEntry("icons", rebuiltIcons)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun ZipOutputStream.copyEntry(source: ZipFile, entry: ZipEntry) {
        val outputEntry = ZipEntry(entry.name).apply {
            time = entry.time
            extra = entry.extra
            comment = entry.comment
            method = if (entry.isDirectory) ZipEntry.STORED else entry.method
            if (method == ZipEntry.STORED) {
                size = if (entry.isDirectory) 0L else entry.size
                compressedSize = size
                crc = if (entry.isDirectory) 0L else entry.crc
            }
        }
        putNextEntry(outputEntry)
        if (!entry.isDirectory) {
            source.getInputStream(entry).use { it.copyTo(this) }
        }
        closeEntry()
    }

    private fun ZipFile.readEntry(name: String): ByteArray? {
        val entry = getEntry(name) ?: return null
        return getInputStream(entry).use(InputStream::readBytes)
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.writeStoredEntry(name: String, file: File) {
        val checksum = CRC32()
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                checksum.update(buffer, 0, count)
            }
        }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = file.length()
            compressedSize = size
            crc = checksum.value
        }
        putNextEntry(entry)
        file.inputStream().buffered().use { it.copyTo(this) }
        closeEntry()
    }

    private fun copyDirectory(from: File, to: File) {
        if (!from.exists()) return
        from.walkTopDown().forEach { source ->
            val target = File(to, source.relativeTo(from).path)
            if (source.isDirectory) target.mkdirs() else {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
            }
        }
    }

    private fun parseXml(input: InputStream): Document {
        return DocumentBuilderFactory.newInstance().apply {
            isIgnoringComments = false
            isCoalescing = true
        }.newDocumentBuilder().parse(input)
    }

    private fun Document.textOf(tag: String): String {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length == 0) "" else nodes.item(0).textContent.orEmpty()
    }

    private fun Document.setTexts(tag: String, value: String) {
        val nodes = getElementsByTagName(tag)
        repeat(nodes.length) { index ->
            val node = nodes.item(index) as Element
            while (node.firstChild != null) node.removeChild(node.firstChild)
            node.appendChild(createCDATASection(value))
        }
    }

    private fun String.removeVariantSuffix(): String {
        return replace(Regex("""_\d+$"""), "")
    }

    private fun variantOrder(variantKey: String, packageName: String): Int {
        if (variantKey == packageName) return 0
        return variantKey.removePrefix("${packageName}_").toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun moveFile(source: File, target: File) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private data class VariantMove(
        val source: File,
        val staged: File,
        val target: File,
    )

    private val CustomizeMessages = Regex("""(?m)^(?:ui_print \"[^\r\n]*\"\r?\n)+""")
    private const val EditableIconDirectory = "res/drawable-xxhdpi/"

    private const val MtzDescriptionTemplate = """<?xml version="1.0" encoding="UTF-8"?><theme>
<version><![CDATA[]]></version>
<uiVersion><![CDATA[17]]></uiVersion>
<author><![CDATA[]]></author>
<designer><![CDATA[]]></designer>
<title><![CDATA[]]></title>
<description><![CDATA[]]></description>
<authors><author locale="zh_CN"><![CDATA[]]></author></authors>
<designers><designer locale="zh_CN"><![CDATA[]]></designer></designers>
<titles><title locale="zh_CN"><![CDATA[]]></title></titles>
<descriptions><description locale="zh_CN"><![CDATA[]]></description></descriptions>
</theme>"""
}
