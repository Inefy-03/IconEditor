package com.bocchi.iconeditor.data

import android.content.Context
import android.net.Uri
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.AppSettings
import com.bocchi.iconeditor.model.ExportFormat
import com.bocchi.iconeditor.model.IconAsset
import com.bocchi.iconeditor.model.IconPreferences
import com.bocchi.iconeditor.model.ProjectIndex
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.model.ProjectSummary
import com.bocchi.iconeditor.model.SourceType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64
import java.util.UUID

class ProjectRepository(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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

    fun importProject(uri: Uri, displayName: String): ProjectSummary {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val sourceType = when (extension) {
            "mtz" -> SourceType.Mtz
            "zip" -> SourceType.Module
            else -> throw InvalidProjectArchiveException()
        }
        val project = createProject(displayName.substringBeforeLast('.').ifBlank { context.getString(R.string.imported_project) })
            .copy(sourceType = sourceType, sourceFileName = displayName)
        return try {
            val target = File(sourceDir(project.id), displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error(context.getString(R.string.error_read_import))
            if (sourceType == SourceType.Module && !ArchiveService.hasTopLevelIconsEntry(target)) {
                throw InvalidProjectArchiveException()
            }

            val imported = try {
                ArchiveService.importArchive(target, sourceType, workDir(project.id), sourceExtractDir(project.id))
            } catch (error: InvalidArchivePathException) {
                error(context.getString(R.string.error_invalid_zip_path, error.entryName))
            }
            saveMetadata(project.id, imported.metadata)
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
    }

    fun exportProject(id: String, format: ExportFormat, target: Uri) {
        val project = requireProject(id)
        val metadata = loadMetadata(id)
        validateForExport(format, metadata).takeIf { it.isNotEmpty() }?.let {
            error(it.joinToString("\n"))
        }
        context.contentResolver.openOutputStream(target)?.use { output ->
            ArchiveService.exportArchive(
                metadata = metadata,
                workDir = workDir(id),
                format = format,
                templateFiles = loadArchiveTemplate(format),
                iconsTemplate = loadIconsTemplate(),
                sourceArchive = sameFormatSourceArchive(project, format),
                output = output,
            )
        } ?: error(context.getString(R.string.error_create_export))
        updateProject(project.copy(dirty = false))
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
        }
    }

    private fun loadArchiveTemplate(format: ExportFormat): Map<String, ByteArray> {
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
        val ModuleTemplateFiles = listOf(
            "META-INF/com/google/android/update-binary",
            "META-INF/com/google/android/updater-script",
            "customize.sh",
            "post-fs-data.sh",
        )
    }
}
