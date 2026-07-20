package com.bocchi.iconeditor.data.sync

import com.bocchi.iconeditor.data.ApkPackAssets
import com.bocchi.iconeditor.data.ArchiveService
import com.bocchi.iconeditor.data.ThemePackAssets
import com.bocchi.iconeditor.model.ProjectSummary
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ProjectSyncPackager {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val imageExt = setOf("png", "webp", "jpg", "jpeg")

    fun packProject(
        projectDir: File,
        project: ProjectSummary,
        inventory: ProjectSyncInventory,
        destination: File,
    ) {
        val staging = File(destination.parentFile, "ie-sync-${UUID.randomUUID()}").also { it.mkdirs() }
        try {
            File(staging, "manifest.json").writeText(
                json.encodeToString(ProjectSyncProjectInfo(project.id, project.name, project.updatedAt)),
            )
            File(staging, "inventory.json").writeText(json.encodeToString(inventory))
            for (name in listOf("work", "source", "source_extract")) {
                val src = File(projectDir, name)
                if (src.isDirectory) src.copyRecursively(File(staging, name), overwrite = true)
            }
            for (name in listOf("metadata.json", "preferences.json", "icon_mapping.json")) {
                val src = File(projectDir, name)
                if (src.isFile) src.copyTo(File(staging, name), overwrite = true)
            }
            if (destination.exists()) destination.delete()
            zipDirectory(staging, destination)
        } finally {
            staging.deleteRecursively()
        }
    }

    fun packIconPackage(workDir: File, packageName: String, destination: File) {
        destination.parentFile?.mkdirs()
        destination.writeBytes(packIconPackageBytes(workDir, packageName))
    }

    fun packIconPackageBytes(workDir: File, packageName: String): ByteArray {
        val files = ArchiveService.listIconFiles(workDir, packageName)
        require(files.isNotEmpty()) { "无图标：$packageName" }
        return ProjectSyncBundle.encodeFiles(files)
    }

    fun applyIconPackageZip(zip: File, workDir: File, packageName: String) {
        applyIconPackageBytes(zip.readBytes(), workDir, packageName)
    }

    fun applyIconPackageBytes(data: ByteArray, workDir: File, packageName: String) {
        ArchiveService.deleteIconFiles(workDir, packageName)
        val iconRoot = File(workDir, "icons/res/drawable-xxhdpi").also { it.mkdirs() }
        val entries = if (ProjectSyncBundle.isBundle(data)) {
            ProjectSyncBundle.decode(data)
                .filter { it.second.isNotEmpty() }
        } else {
            // Legacy zip from older peers.
            val staged = mutableListOf<Pair<String, ByteArray>>()
            ZipInputStream(BufferedInputStream(java.io.ByteArrayInputStream(data))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast('/')
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in imageExt) staged += name to zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            staged
        }
        // 保留原始文件名，避免 _2 被改成 _1 导致两端指纹永远对不上。
        for ((name, bytes) in entries) {
            val safeName = File(name).name
            if (safeName.isBlank() || safeName == "." || safeName == "..") continue
            File(iconRoot, safeName).writeBytes(bytes)
        }
    }

    /** Pick the primary variant image bytes from a sync icon package (bundle or legacy zip). */
    fun primaryImageFromPackage(data: ByteArray, packageName: String): ByteArray? {
        val entries = if (ProjectSyncBundle.isBundle(data)) {
            ProjectSyncBundle.decode(data).filter { it.second.isNotEmpty() }
        } else {
            val staged = mutableListOf<Pair<String, ByteArray>>()
            ZipInputStream(BufferedInputStream(java.io.ByteArrayInputStream(data))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast('/')
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in imageExt) staged += name to zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            staged
        }
        if (entries.isEmpty()) return null
        val primary = entries.firstOrNull { (name, _) ->
            File(name).nameWithoutExtension.equals(packageName, ignoreCase = false)
        } ?: entries.minByOrNull { (name, _) ->
            val base = File(name).nameWithoutExtension
            when {
                base == packageName -> 0
                base.startsWith("${packageName}_") ->
                    base.removePrefix("${packageName}_").toIntOrNull() ?: Int.MAX_VALUE
                else -> Int.MAX_VALUE
            }
        }
        return primary?.second
    }

    fun packMeta(projectDir: File, workDir: File, destination: File) {
        destination.parentFile?.mkdirs()
        destination.writeBytes(packMetaBytes(projectDir, workDir))
    }

    fun packMetaBytes(projectDir: File, workDir: File): ByteArray {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        for (name in listOf("metadata.json", "preferences.json", "icon_mapping.json")) {
            val src = File(projectDir, name)
            if (src.isFile) entries += name to src.readBytes()
        }
        for (relative in ManagedMetaAssetPaths) {
            val src = File(workDir, relative)
            if (src.isFile) entries += relative to src.readBytes()
        }
        return ProjectSyncBundle.encode(entries)
    }

    fun applyMeta(zip: File, projectDir: File, workDir: File) {
        applyMetaBytes(zip.readBytes(), projectDir, workDir)
    }

    fun applyMetaBytes(data: ByteArray, projectDir: File, workDir: File) {
        val entries = if (ProjectSyncBundle.isBundle(data)) {
            ProjectSyncBundle.decode(data)
        } else {
            val staging = File(projectDir.parentFile ?: projectDir, "ie-meta-in-${UUID.randomUUID()}").also { it.mkdirs() }
            try {
                val tmp = File(staging, "legacy.zip").also { it.writeBytes(data) }
                unzip(tmp, staging)
                staging.walkTopDown().filter { it.isFile && it.name != "legacy.zip" }.map {
                    it.relativeTo(staging).invariantSeparatorsPath to it.readBytes()
                }.toList()
            } finally {
                staging.deleteRecursively()
            }
        }
        val incomingNames = entries.mapTo(mutableSetOf()) { it.first }
        ManagedMetaAssetPaths
            .filterNot(incomingNames::contains)
            .forEach { relative -> File(workDir, relative).delete() }
        for ((name, bytes) in entries) {
            when (name) {
                "metadata.json", "preferences.json", "icon_mapping.json" ->
                    File(projectDir, name).writeBytes(bytes)
                else -> {
                    val dest = File(workDir, name)
                    dest.parentFile?.mkdirs()
                    dest.writeBytes(bytes)
                }
            }
        }
        ArchiveService.syncIconMaskTransformConfig(workDir)
    }

    fun readInventoryFromZip(zip: File): ProjectSyncInventory {
        val staging = File(zip.parentFile, "ie-inv-${UUID.randomUUID()}").also { it.mkdirs() }
        try {
            unzip(zip, staging)
            val inv = File(staging, "inventory.json")
            require(inv.isFile) { "同步包缺少 inventory.json" }
            return json.decodeFromString(ProjectSyncInventory.serializer(), inv.readText())
        } finally {
            staging.deleteRecursively()
        }
    }

    fun unzip(zip: File, targetDir: File) {
        targetDir.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(out)).use { bos -> zis.copyTo(bos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val name = file.relativeTo(sourceDir).invariantSeparatorsPath
                zos.putNextEntry(ZipEntry(name))
                FileInputStream(file).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private val ManagedMetaAssetPaths = listOf(
        ApkPackAssets.LAUNCHER_ICON_PATH,
        ApkPackAssets.MaskLayer.Back.relativePath,
        ApkPackAssets.MaskLayer.Mask.relativePath,
        ApkPackAssets.MaskLayer.Upon.relativePath,
    ) + ThemePackAssets.syncRelativePaths
}
