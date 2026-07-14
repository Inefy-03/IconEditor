package com.bocchi.iconeditor.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.ApkInfo
import com.bocchi.iconeditor.model.AppSettings
import com.bocchi.iconeditor.model.ExportFormat
import com.bocchi.iconeditor.model.ExportProgress
import com.bocchi.iconeditor.model.ExportPhase
import com.bocchi.iconeditor.model.IconAsset
import com.bocchi.iconeditor.model.IconImportCandidate
import com.bocchi.iconeditor.model.IconImportMode
import com.bocchi.iconeditor.model.IconImportPreview
import com.bocchi.iconeditor.model.MaskLayerImportCandidate
import com.bocchi.iconeditor.model.MaskLayerImportPreview
import com.bocchi.iconeditor.model.IconMappingIndex
import com.bocchi.iconeditor.model.IconPreferences
import com.bocchi.iconeditor.model.ProjectIndex
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.model.ProjectSummary
import com.bocchi.iconeditor.model.SourceType
import com.bocchi.iconeditor.model.ImportPhase
import com.bocchi.iconeditor.model.ImportProgress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.UUID

class ProjectRepository(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val apkImporter = ApkIconPackImporter(context)
    private val apkExporter = ApkIconPackExporter(context)
    private val maskLayerExtractor = MaskLayerExtractor(context)

    private val root: File = File(context.filesDir, "projects")
    private val indexFile: File = File(root, "index.json")
    private val settingsFile: File = File(context.filesDir, "settings.json")

    init {
        root.mkdirs()
    }

    fun loadProjects(): List<ProjectSummary> = readJson(indexFile, ProjectIndex()).projects

    fun loadSettings(): AppSettings = readJson(settingsFile, AppSettings())

    fun saveSettings(settings: AppSettings) {
        writeJson(settingsFile, settings)
    }

    fun createProject(name: String = context.getString(R.string.new_project)): ProjectSummary {
        val project = ProjectSummary(
            id = UUID.randomUUID().toString(),
            name = uniqueProjectName(name),
        )
        projectDir(project.id).mkdirs()
        workDir(project.id).mkdirs()
        sourceDir(project.id).mkdirs()
        sourceExtractDir(project.id).mkdirs()
        ArchiveService.createDefaultWorkspace(workDir(project.id))
        ArchiveService.createDefaultWorkspace(sourceExtractDir(project.id))
        saveMetadata(project.id, ProjectMetadata())
        saveIconPreferences(project.id, IconPreferences())
        updateProject(project)
        return project
    }

    fun importProject(
        uri: Uri,
        displayName: String,
        onProgress: (ImportProgress) -> Unit = {},
    ): ProjectSummary {
        val resolvedName = ImportSourceDetector.resolveDisplayName(context, uri)
        val sourceType = ImportSourceDetector.detect(context, uri, resolvedName)
        val project = createProject(resolvedName.substringBeforeLast('.').ifBlank { context.getString(R.string.imported_project) })
            .copy(sourceType = sourceType, sourceFileName = resolvedName)
        return try {
            onProgress(ImportProgress(ImportPhase.Copying))
            val target = File(sourceDir(project.id), resolvedName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error(context.getString(R.string.error_read_import))
            when (sourceType) {
                SourceType.Module -> {
                    onProgress(ImportProgress(ImportPhase.Extracting))
                    if (!ArchiveService.hasTopLevelIconsEntry(target)) {
                        throw InvalidProjectArchiveException()
                    }
                    val imported = try {
                        ArchiveService.importArchive(target, sourceType, workDir(project.id), sourceExtractDir(project.id))
                    } catch (error: InvalidArchivePathException) {
                        error(context.getString(R.string.error_invalid_zip_path, error.entryName))
                    }
                    saveMetadata(project.id, imported.metadata)
                    onProgress(ImportProgress(ImportPhase.Finishing))
                    syncIconMapping(project.id)
                }
                SourceType.Mtz -> {
                    onProgress(ImportProgress(ImportPhase.Extracting))
                    val imported = try {
                        ArchiveService.importArchive(target, sourceType, workDir(project.id), sourceExtractDir(project.id))
                    } catch (error: InvalidArchivePathException) {
                        error(context.getString(R.string.error_invalid_zip_path, error.entryName))
                    }
                    saveMetadata(project.id, imported.metadata)
                    onProgress(ImportProgress(ImportPhase.Finishing))
                    syncIconMapping(project.id)
                }
                SourceType.Apk -> {
                    if (!apkImporter.hasAppfilter(target)) {
                        throw InvalidIconPackApkException()
                    }
                    val imported = apkImporter.import(
                        apkFile = target,
                        workDir = workDir(project.id),
                        sourceExtractDir = sourceExtractDir(project.id),
                        onProgress = onProgress,
                    )
                    saveMetadata(project.id, imported.metadata)
                    saveIconMapping(project.id, imported.mapping)
                    onProgress(ImportProgress(ImportPhase.Finishing))
                }
                SourceType.Universal -> Unit
            }
            saveIconPreferences(project.id, IconPreferences())
            updateProject(project.copy(updatedAt = System.currentTimeMillis()))
            project
        } catch (error: Throwable) {
            runCatching { deleteProject(project.id) }
            throw error
        }
    }

    fun deleteProject(id: String) {
        projectDir(id).deleteRecursively()
        saveIndex(loadProjects().filterNot { it.id == id })
    }

    fun renameProject(id: String, name: String) {
        val trimmed = name.trim().ifBlank { context.getString(R.string.unnamed_project) }
        updateProject(requireProject(id).copy(name = trimmed, updatedAt = System.currentTimeMillis()))
    }

    fun requireProject(id: String): ProjectSummary = loadProjects().first { it.id == id }

    fun loadMetadata(id: String): ProjectMetadata = readJson(metadataFile(id), ProjectMetadata())

    fun saveMetadata(id: String, metadata: ProjectMetadata) {
        writeJson(metadataFile(id), metadata)
        markDirty(id)
    }

    fun loadIconPreferences(id: String): IconPreferences = readJson(preferencesFile(id), IconPreferences())

    fun saveIconPreferences(id: String, preferences: IconPreferences) {
        writeJson(preferencesFile(id), preferences)
    }

    fun loadIconMapping(id: String): IconMappingIndex = readJson(iconMappingFile(id), IconMappingIndex())

    fun saveIconMapping(id: String, mapping: IconMappingIndex) {
        writeJson(iconMappingFile(id), mapping)
    }

    fun syncIconMapping(id: String) {
        val icons = loadIcons(id)
        val existing = loadIconMapping(id)
        val mapping = if (existing.entries.isEmpty()) {
            IconMappingBridge.buildDefaultMappings(context, icons)
        } else {
            IconMappingBridge.mergeMappingsWithIcons(existing, icons, context.packageManager)
        }
        saveIconMapping(id, mapping)
    }

    fun loadIcons(id: String): List<IconAsset> = ArchiveService.scanIconAssets(workDir(id))

    fun iconFile(id: String, asset: IconAsset): File = File(workDir(id), asset.archivePath)

    fun replaceIcon(id: String, asset: IconAsset, uri: Uri) {
        val target = File(workDir(id), asset.archivePath)
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error(context.getString(R.string.error_read_icon))
        markDirty(id)
    }

    fun apkLauncherIconFile(id: String): File? =
        File(workDir(id), ApkPackAssets.LAUNCHER_ICON_PATH).takeIf { it.isFile }

    fun apkMaskLayerFile(id: String, layer: ApkPackAssets.MaskLayer): File? {
        migrateLegacyMaskIfNeeded(id)
        return File(workDir(id), layer.relativePath).takeIf { it.isFile }
    }

    fun setApkLauncherIcon(id: String, uri: Uri) {
        writeApkPngAsset(id, ApkPackAssets.LAUNCHER_ICON_PATH, uri)
    }

    fun setApkMaskLayer(id: String, layer: ApkPackAssets.MaskLayer, uri: Uri) {
        writeApkPngAsset(id, layer.relativePath, uri)
        if (layer == ApkPackAssets.MaskLayer.Mask) {
            File(workDir(id), ApkPackAssets.LEGACY_MASK_PATH).delete()
        }
    }

    fun clearApkLauncherIcon(id: String) {
        File(workDir(id), ApkPackAssets.LAUNCHER_ICON_PATH).delete()
        markDirty(id)
    }

    fun clearApkMaskLayer(id: String, layer: ApkPackAssets.MaskLayer) {
        File(workDir(id), layer.relativePath).delete()
        if (layer == ApkPackAssets.MaskLayer.Mask) {
            File(workDir(id), ApkPackAssets.LEGACY_MASK_PATH).delete()
        }
        markDirty(id)
    }

    private fun migrateLegacyMaskIfNeeded(id: String) {
        val legacy = File(workDir(id), ApkPackAssets.LEGACY_MASK_PATH)
        val target = File(workDir(id), ApkPackAssets.MaskLayer.Mask.relativePath)
        if (legacy.isFile && !target.isFile) {
            target.parentFile?.mkdirs()
            legacy.copyTo(target, overwrite = false)
        }
    }

    private fun writeApkPngAsset(id: String, relativePath: String, uri: Uri) {
        val target = File(workDir(id), relativePath)
        target.parentFile?.mkdirs()
        val bitmap = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: error(context.getString(R.string.error_read_icon))
        FileOutputStream(target).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        markDirty(id)
    }

    fun addIconVariant(id: String, packageName: String, uri: Uri): String {
        val variants = loadIcons(id).filter { it.packageName == packageName }
        val usedKeys = variants.mapTo(mutableSetOf()) { it.variantKey }
        val nextSuffix = variants.mapNotNull { asset ->
            asset.variantKey.removePrefix("${packageName}_").toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1
        val variantKey = if (variants.isEmpty()) {
            packageName
        } else {
            generateSequence(nextSuffix) { it + 1 }
                .map { index -> "${packageName}_$index" }
                .first { it !in usedKeys }
        }
        val fileName = "$variantKey.png"
        val target = File(workDir(id), "icons/res/drawable-xxhdpi/$fileName")
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error(context.getString(R.string.error_read_icon))
        markDirty(id)
        syncIconMapping(id)
        return variantKey
    }

    fun normalizeIconVariants(id: String, packageName: String, selectedVariantKey: String?): String? {
        return ArchiveService.normalizeIconVariants(workDir(id), packageName, selectedVariantKey)
            .also { if (it != null) markDirty(id) }
    }

    fun resetIcon(id: String, asset: IconAsset) {
        val source = File(sourceExtractDir(id), asset.archivePath)
        val target = File(workDir(id), asset.archivePath)
        if (source.exists()) {
            source.copyTo(target, overwrite = true)
        } else {
            target.delete()
        }
        markDirty(id)
    }

    fun deleteIcon(id: String, asset: IconAsset) {
        File(workDir(id), asset.archivePath).delete()
        markDirty(id)
        syncIconMapping(id)
    }

    fun renameIconPackage(id: String, from: String, to: String) {
        ArchiveService.renameIconPackage(workDir(id), from, to)
        markDirty(id)
        // Remap selected variants and mapping package key are handled by callers;
        // sync rebuilds entries for on-disk icons while preserving aliases of remaining keys.
        val existing = loadIconMapping(id)
        val remapped = IconMappingIndex(
            entries = existing.entries.map { entry ->
                if (entry.packageName == from) {
                    entry.copy(
                        packageName = to,
                        components = entry.components.map { component ->
                            val pkg = IconMappingBridge.parseComponentInfo(component)?.first
                            if (pkg == from) {
                                IconMappingBridge.fallbackComponent(to)
                            } else {
                                component
                            }
                        }.distinct(),
                    )
                } else {
                    entry
                }
            },
        )
        saveIconMapping(id, remapped)
        syncIconMapping(id)
    }

    fun updateIconAliasPackageNames(id: String, packageName: String, aliases: List<String>) {
        val normalized = IconMappingBridge.normalizeAliasPackageNames(aliases, packageName)
        syncIconMapping(id)
        val mapping = loadIconMapping(id)
        val updated = IconMappingIndex(
            entries = mapping.entries.map { entry ->
                if (entry.packageName == packageName) {
                    entry.copy(aliasPackageNames = normalized)
                } else {
                    entry
                }
            },
        )
        saveIconMapping(id, updated)
    }

    fun previewIconsFromPack(
        projectId: String,
        uri: Uri,
        onProgress: (ImportProgress) -> Unit = {},
    ): IconImportPreview {
        requireProject(projectId)
        onProgress(ImportProgress(ImportPhase.Copying))
        val displayName = ImportSourceDetector.resolveDisplayName(context, uri)
        val sourceType = ImportSourceDetector.detect(context, uri, displayName)
        val stagingId = UUID.randomUUID().toString()
        val stagingRoot = iconImportStagingRoot(stagingId).also { it.mkdirs() }
        return try {
            val sourceFile = File(stagingRoot, displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                sourceFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error(context.getString(R.string.error_read_import))

            val stagingWork = File(stagingRoot, "work")
            val stagingExtract = File(stagingRoot, "extract")
            val incomingMapping = when (sourceType) {
                SourceType.Module -> {
                    onProgress(ImportProgress(ImportPhase.Extracting))
                    if (!ArchiveService.hasTopLevelIconsEntry(sourceFile)) {
                        throw InvalidProjectArchiveException()
                    }
                    try {
                        ArchiveService.importArchive(sourceFile, sourceType, stagingWork, stagingExtract)
                    } catch (error: InvalidArchivePathException) {
                        error(context.getString(R.string.error_invalid_zip_path, error.entryName))
                    }
                    IconMappingIndex()
                }
                SourceType.Mtz -> {
                    onProgress(ImportProgress(ImportPhase.Extracting))
                    try {
                        ArchiveService.importArchive(sourceFile, sourceType, stagingWork, stagingExtract)
                    } catch (error: InvalidArchivePathException) {
                        error(context.getString(R.string.error_invalid_zip_path, error.entryName))
                    }
                    IconMappingIndex()
                }
                SourceType.Apk -> {
                    if (!apkImporter.hasAppfilter(sourceFile)) {
                        throw InvalidIconPackApkException()
                    }
                    val imported = apkImporter.import(
                        apkFile = sourceFile,
                        workDir = stagingWork,
                        sourceExtractDir = stagingExtract,
                        onProgress = onProgress,
                    )
                    imported.mapping
                }
                SourceType.Universal -> error(context.getString(R.string.error_read_import))
            }
            saveStagedMapping(stagingId, incomingMapping)

            onProgress(ImportProgress(ImportPhase.Finishing))
            val incomingIcons = ArchiveService.scanIconAssets(stagingWork)
            val primaryByPackage = incomingIcons
                .groupBy { it.packageName }
                .mapValues { (_, assets) ->
                    assets.minWith(
                        compareBy<IconAsset> {
                            val suffix = it.variantKey.removePrefix(it.packageName)
                            if (suffix.isEmpty()) 0 else suffix.removePrefix("_").toIntOrNull() ?: Int.MAX_VALUE
                        }.thenBy { it.variantKey },
                    )
                }
            if (primaryByPackage.isEmpty()) {
                throw InvalidProjectArchiveException()
            }
            val existingPackages = loadIcons(projectId).map { it.packageName }.toSet()
            val pm = context.packageManager
            val items = primaryByPackage.keys.sorted().map { packageName ->
                val asset = primaryByPackage.getValue(packageName)
                val installedName = runCatching {
                    val info = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(info).toString()
                }.getOrNull()
                IconImportCandidate(
                    packageName = packageName,
                    appName = installedName?.takeIf { it.isNotBlank() } ?: packageName,
                    iconArchivePath = asset.archivePath,
                    conflict = packageName in existingPackages,
                    selected = true,
                )
            }
            IconImportPreview(
                stagingId = stagingId,
                sourceType = sourceType,
                displayName = displayName,
                items = items,
            )
        } catch (error: Throwable) {
            discardIconImportStaging(stagingId)
            throw error
        }
    }

    fun applyIconImport(
        projectId: String,
        preview: IconImportPreview,
        mode: IconImportMode,
        selectedPackages: Set<String>,
        onProgress: (ImportProgress) -> Unit = {},
    ) {
        requireProject(projectId)
        val stagingWork = File(iconImportStagingRoot(preview.stagingId), "work")
        if (!stagingWork.isDirectory) {
            error(context.getString(R.string.icon_import_staging_missing))
        }
        onProgress(ImportProgress(ImportPhase.Copying))
        val targetWork = workDir(projectId)
        val targetIconRoot = File(targetWork, "icons/res/drawable-xxhdpi").also { it.mkdirs() }
        val incomingIcons = ArchiveService.scanIconAssets(stagingWork)
        val incomingByPackage = incomingIcons.groupBy { it.packageName }
        val existingByPackage = loadIcons(projectId).groupBy { it.packageName }
        val packagesToApply = when (mode) {
            IconImportMode.Overwrite -> selectedPackages.filter { it in incomingByPackage }
            IconImportMode.AddOnly -> selectedPackages.filter {
                it in incomingByPackage && it !in existingByPackage
            }
        }.sorted()
        if (packagesToApply.isEmpty()) {
            error(context.getString(R.string.icon_import_nothing_selected))
        }
        val total = packagesToApply.size
        packagesToApply.forEachIndexed { index, packageName ->
            onProgress(ImportProgress(ImportPhase.ParsingIcons, current = index + 1, total = total))
            existingByPackage[packageName].orEmpty().forEach { asset ->
                File(targetWork, asset.archivePath).delete()
            }
            incomingByPackage[packageName].orEmpty().forEach { asset ->
                val source = File(stagingWork, asset.archivePath)
                if (!source.isFile) return@forEach
                val target = File(targetIconRoot, source.name)
                source.copyTo(target, overwrite = true)
            }
        }

        onProgress(ImportProgress(ImportPhase.Finishing))
        val incomingMapping = loadStagedMapping(preview.stagingId)
        val preferred = IconMappingBridge.preferIncomingMappings(
            existing = loadIconMapping(projectId),
            incoming = incomingMapping,
            packages = packagesToApply.toSet(),
        )
        val synced = IconMappingBridge.mergeMappingsWithIcons(
            existing = preferred,
            icons = loadIcons(projectId),
            pm = context.packageManager,
        )
        saveIconMapping(projectId, synced)
        markDirty(projectId)
        discardIconImportStaging(preview.stagingId)
    }

    fun iconImportCandidateFile(stagingId: String, iconArchivePath: String): File =
        File(iconImportStagingRoot(stagingId), "work/$iconArchivePath")

    fun discardIconImportStaging(stagingId: String) {
        runCatching { iconImportStagingRoot(stagingId).deleteRecursively() }
    }

    fun previewMaskLayersFromPack(
        projectId: String,
        uri: Uri,
        onProgress: (ImportProgress) -> Unit = {},
    ): MaskLayerImportPreview {
        requireProject(projectId)
        onProgress(ImportProgress(ImportPhase.Copying))
        val displayName = ImportSourceDetector.resolveDisplayName(context, uri)
        val sourceType = ImportSourceDetector.detect(context, uri, displayName)
        val stagingId = UUID.randomUUID().toString()
        val stagingRoot = maskImportStagingRoot(stagingId).also { it.mkdirs() }
        return try {
            val sourceFile = File(stagingRoot, displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                sourceFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error(context.getString(R.string.error_read_import))

            val layersDir = File(stagingRoot, "layers").also { it.mkdirs() }
            onProgress(ImportProgress(ImportPhase.Extracting))
            val extracted = when (sourceType) {
                SourceType.Apk -> maskLayerExtractor.extractFromApk(sourceFile, layersDir)
                SourceType.Module -> {
                    if (!ArchiveService.hasTopLevelIconsEntry(sourceFile)) {
                        throw InvalidProjectArchiveException()
                    }
                    val stagingWork = File(stagingRoot, "work")
                    val stagingExtract = File(stagingRoot, "extract")
                    try {
                        ArchiveService.importArchive(sourceFile, sourceType, stagingWork, stagingExtract)
                    } catch (error: InvalidArchivePathException) {
                        error(context.getString(R.string.error_invalid_zip_path, error.entryName))
                    }
                    maskLayerExtractor.extractFromWorkDir(stagingWork, layersDir)
                }
                SourceType.Mtz -> {
                    val stagingWork = File(stagingRoot, "work")
                    val stagingExtract = File(stagingRoot, "extract")
                    try {
                        ArchiveService.importArchive(sourceFile, sourceType, stagingWork, stagingExtract)
                    } catch (error: InvalidArchivePathException) {
                        error(context.getString(R.string.error_invalid_zip_path, error.entryName))
                    }
                    maskLayerExtractor.extractFromWorkDir(stagingWork, layersDir)
                }
                SourceType.Universal -> error(context.getString(R.string.error_read_import))
            }
            if (extracted.isEmpty()) {
                throw NoMaskLayersFoundException()
            }
            onProgress(ImportProgress(ImportPhase.Finishing))
            val items = ApkPackAssets.MaskLayer.entries.map { layer ->
                val found = layer in extracted
                MaskLayerImportCandidate(
                    layerName = layer.resourceName,
                    found = found,
                    conflict = found && apkMaskLayerFile(projectId, layer) != null,
                    selected = found,
                )
            }
            MaskLayerImportPreview(
                stagingId = stagingId,
                sourceType = sourceType,
                displayName = displayName,
                items = items,
            )
        } catch (error: Throwable) {
            discardMaskImportStaging(stagingId)
            throw error
        }
    }

    fun applyMaskLayerImport(
        projectId: String,
        preview: MaskLayerImportPreview,
        selectedLayers: Set<String>,
        onProgress: (ImportProgress) -> Unit = {},
    ) {
        requireProject(projectId)
        val layersDir = File(maskImportStagingRoot(preview.stagingId), "layers")
        if (!layersDir.isDirectory) {
            error(context.getString(R.string.mask_import_staging_missing))
        }
        onProgress(ImportProgress(ImportPhase.Copying))
        var imported = 0
        ApkPackAssets.MaskLayer.entries.forEach { layer ->
            if (layer.resourceName !in selectedLayers) return@forEach
            val source = File(layersDir, "${layer.resourceName}.png")
            if (!source.isFile) return@forEach
            val target = File(workDir(projectId), layer.relativePath)
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            if (layer == ApkPackAssets.MaskLayer.Mask) {
                File(workDir(projectId), ApkPackAssets.LEGACY_MASK_PATH).delete()
            }
            imported++
        }
        if (imported == 0) {
            error(context.getString(R.string.mask_import_nothing_selected))
        }
        markDirty(projectId)
        onProgress(ImportProgress(ImportPhase.Finishing))
        discardMaskImportStaging(preview.stagingId)
    }

    fun maskImportLayerFile(stagingId: String, layerName: String): File? =
        File(maskImportStagingRoot(stagingId), "layers/$layerName.png").takeIf { it.isFile }

    fun discardMaskImportStaging(stagingId: String) {
        runCatching { maskImportStagingRoot(stagingId).deleteRecursively() }
    }

    fun exportProject(
        id: String,
        format: ExportFormat,
        target: Uri,
        onProgress: (ExportProgress) -> Unit = {},
    ) {
        val reporter = ExportProgressReporter(onProgress)
        val project = requireProject(id)
        val formatLabel = when (format) {
            ExportFormat.Mtz -> "MTZ"
            ExportFormat.ModuleZip -> "Module"
            ExportFormat.Apk -> "APK"
        }
        reporter.update(
            phase = ExportPhase.Preparing,
            log = "开始导出 $formatLabel：${project.name}",
        )
        val metadata = resolveExportMetadata(id, format, persist = true)
        reporter.log("校验导出信息")
        validateForExport(format, metadata).takeIf { it.isNotEmpty() }?.let {
            error(it.joinToString("\n"))
        }
        val icons = loadIcons(id)
        reporter.log("已加载 ${icons.size} 个图标资源")
        val apkMapping = if (format == ExportFormat.Apk) {
            reporter.update(detail = "生成 APK 映射")
            reporter.log("生成 APK 图标映射")
            IconMappingBridge.prepareApkExportMapping(context, icons, loadIconMapping(id)).also {
                saveIconMapping(id, it)
                reporter.log("映射条目：${it.entries.size}")
            }
        } else {
            syncIconMapping(id)
            IconMappingIndex()
        }
        reporter.update(phase = ExportPhase.WritingArchive, detail = "写入目标文件")
        reporter.log("打开导出目标")
        try {
            context.contentResolver.openOutputStream(target, "w")?.use { output ->
                when (format) {
                    ExportFormat.Mtz, ExportFormat.ModuleZip -> {
                        reporter.log("打包 $formatLabel 归档")
                        ArchiveService.exportArchive(
                            metadata = metadata,
                            workDir = workDir(id),
                            format = format,
                            templateFiles = loadArchiveTemplate(format),
                            iconsTemplate = loadIconsTemplate(),
                            sourceArchive = sameFormatSourceArchive(project, format),
                            output = output,
                            reporter = reporter,
                        )
                    }
                    ExportFormat.Apk -> apkExporter.export(
                        apkInfo = metadata.apk,
                        workDir = workDir(id),
                        mapping = apkMapping,
                        icons = icons,
                        sourceApk = sameFormatSourceArchive(project, format),
                        output = output,
                        reporter = reporter,
                    )
                }
                reporter.log("写入完成")
            } ?: error(context.getString(R.string.error_create_export))
            ExportDirectoryHelper.finalizeExportIfNeeded(context, target)
        } catch (error: Exception) {
            ExportDirectoryHelper.abortPendingExport(context, target)
            throw error
        }
        reporter.update(phase = ExportPhase.Finishing, detail = "")
        reporter.log("导出完成")
        updateProject(project.copy(dirty = false))
    }

    /**
     * 将刚导出的 APK 复制到应用缓存并返回 FileProvider URI。
     * 安装器直接读 Downloads/SAF 的 content URI 时，部分机型会缓存旧文件。
     */
    fun prepareInstallableApkUri(sourceUri: Uri): Uri {
        val installDir = File(context.cacheDir, "apk-install").apply { mkdirs() }
        installDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_INSTALL_COPIES)
            ?.forEach { runCatching { it.delete() } }
        val target = File(installDir, "install-${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error(context.getString(R.string.error_read_import))
        require(target.isFile && target.length() > 0L) {
            context.getString(R.string.export_apk_install_failed)
        }
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
    }

    fun validateForExport(id: String, format: ExportFormat): List<String> {
        val metadata = resolveExportMetadata(id, format, persist = false)
        return validateForExport(format, metadata)
    }

    fun validateForExport(format: ExportFormat, metadata: ProjectMetadata): List<String> {
        return when (format) {
            ExportFormat.Mtz -> buildList {
                if (metadata.mtz.version.isBlank()) add(context.getString(R.string.validation_mtz_version))
                if (metadata.mtz.author.isBlank()) add(context.getString(R.string.validation_mtz_author))
                if (metadata.mtz.designer.isBlank()) add(context.getString(R.string.validation_mtz_designer))
                if (metadata.mtz.title.isBlank()) add(context.getString(R.string.validation_mtz_title))
            }
            ExportFormat.ModuleZip -> buildList {
                if (metadata.module.id.isBlank()) add(context.getString(R.string.validation_module_id))
                if (metadata.module.name.isBlank()) add(context.getString(R.string.validation_module_name))
                if (metadata.module.author.isBlank()) add(context.getString(R.string.validation_module_author))
                if (metadata.module.version.isBlank()) add(context.getString(R.string.validation_module_version))
                if (metadata.module.theme.isBlank()) add(context.getString(R.string.validation_module_theme))
                if (metadata.module.themeId.isBlank()) add(context.getString(R.string.validation_module_theme_id))
                if (!metadata.module.id.matches(Regex("^[a-zA-Z][a-zA-Z0-9._-]+$"))) {
                    add(context.getString(R.string.validation_module_id_format))
                }
            }
            ExportFormat.Apk -> buildList {
                if (metadata.apk.packageName.isBlank()) {
                    add(context.getString(R.string.validation_apk_package))
                } else if (!ApkInfoDefaults.isValidPackageName(metadata.apk.packageName)) {
                    add(context.getString(R.string.validation_apk_package_format))
                }
                if (metadata.apk.label.isBlank()) {
                    add(context.getString(R.string.validation_apk_label))
                }
                if (metadata.apk.versionName.isBlank()) {
                    add(context.getString(R.string.validation_apk_version))
                }
            }
        }
    }

    private fun resolveExportMetadata(id: String, format: ExportFormat, persist: Boolean): ProjectMetadata {
        val metadata = loadMetadata(id)
        if (format != ExportFormat.Apk) return metadata
        val project = requireProject(id)
        val resolvedApk = ApkInfoDefaults.resolve(project.name, metadata)
        if (resolvedApk == metadata.apk) return metadata
        val updated = metadata.copy(apk = resolvedApk)
        if (persist) saveMetadata(id, updated)
        return updated
    }

    private fun loadArchiveTemplate(format: ExportFormat): Map<String, ByteArray> {
        if (format == ExportFormat.Apk) return emptyMap()
        if (format == ExportFormat.Mtz) return mapOf("com.miui.home" to "test".encodeToByteArray())
        return ModuleTemplateFiles.associateWith { name ->
            context.assets.open("archive_templates/module/$name").use { it.readBytes() }
        }
    }

    private fun loadIconsTemplate(): ByteArray {
        val encoded = context.assets.open("archive_templates/icons.b64").bufferedReader().use { it.readText() }
        return Base64.getMimeDecoder().decode(encoded)
    }

    private fun sameFormatSourceArchive(project: ProjectSummary, format: ExportFormat): File? {
        val sameFormat = when (format) {
            ExportFormat.Mtz -> project.sourceType == SourceType.Mtz
            ExportFormat.ModuleZip -> project.sourceType == SourceType.Module
            ExportFormat.Apk -> project.sourceType == SourceType.Apk
        }
        if (!sameFormat || project.sourceFileName.isBlank()) return null
        return File(sourceDir(project.id), project.sourceFileName).takeIf(File::isFile)
    }

    private fun uniqueProjectName(baseName: String): String {
        val used = loadProjects().map { it.name }.toSet()
        val clean = baseName.trim().ifBlank { context.getString(R.string.new_project) }
        if (clean !in used) return clean
        var index = 2
        while ("$clean $index" in used) index++
        return "$clean $index"
    }

    private fun updateProject(project: ProjectSummary) {
        val projects = loadProjects().filterNot { it.id == project.id } + project
        saveIndex(projects.sortedByDescending { it.updatedAt })
    }

    private fun markDirty(id: String) {
        val current = loadProjects().firstOrNull { it.id == id } ?: return
        updateProject(current.copy(dirty = true, updatedAt = System.currentTimeMillis()))
    }

    private fun saveIndex(projects: List<ProjectSummary>) {
        writeJson(indexFile, ProjectIndex(projects))
    }

    private fun projectDir(id: String) = File(root, id)
    private fun sourceDir(id: String) = File(projectDir(id), "source")
    private fun sourceExtractDir(id: String) = File(projectDir(id), "source_extract")
    private fun workDir(id: String) = File(projectDir(id), "work")
    private fun metadataFile(id: String) = File(projectDir(id), "metadata.json")
    private fun preferencesFile(id: String) = File(projectDir(id), "preferences.json")
    private fun iconMappingFile(id: String) = File(projectDir(id), "icon_mapping.json")
    private fun iconImportStagingRoot(stagingId: String) = File(context.cacheDir, "icon-import/$stagingId")
    private fun maskImportStagingRoot(stagingId: String) = File(context.cacheDir, "mask-import/$stagingId")
    private fun stagedMappingFile(stagingId: String) = File(iconImportStagingRoot(stagingId), "mapping.json")

    private fun saveStagedMapping(stagingId: String, mapping: IconMappingIndex) {
        writeJson(stagedMappingFile(stagingId), mapping)
    }

    private fun loadStagedMapping(stagingId: String): IconMappingIndex =
        readJson(stagedMappingFile(stagingId), IconMappingIndex())

    private inline fun <reified T> readJson(file: File, fallback: T): T {
        return runCatching {
            if (!file.exists()) fallback else json.decodeFromString<T>(file.readText())
        }.getOrElse { fallback }
    }

    private inline fun <reified T> writeJson(file: File, value: T) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(value))
    }

    private companion object {
        const val KEEP_INSTALL_COPIES = 3
        val ModuleTemplateFiles = listOf(
            "META-INF/com/google/android/update-binary",
            "META-INF/com/google/android/updater-script",
            "customize.sh",
            "post-fs-data.sh",
        )
    }
}
