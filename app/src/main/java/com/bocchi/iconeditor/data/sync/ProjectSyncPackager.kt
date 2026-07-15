package com.bocchi.iconeditor.data.sync

import com.bocchi.iconeditor.data.ApkPackAssets
import com.bocchi.iconeditor.data.ArchiveService
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
        val assets = ArchiveService.scanIconAssets(workDir).filter { it.packageName == packageName }
        require(assets.isNotEmpty()) { "无图标：$packageName" }
        val staging = File(destination.parentFile, "ie-icon-${UUID.randomUUID()}").also { it.mkdirs() }
        try {
            for (asset in assets) {
                File(workDir, asset.archivePath).copyTo(File(staging, asset.fileName), overwrite = true)
            }
            if (destination.exists()) destination.delete()
            zipDirectory(staging, destination)
        } finally {
            staging.deleteRecursively()
        }
    }

    fun applyIconPackageZip(zip: File, workDir: File, packageName: String) {
        for (asset in ArchiveService.scanIconAssets(workDir).filter { it.packageName == packageName }) {
            File(workDir, asset.archivePath).delete()
        }
        val staging = File(workDir.parentFile ?: workDir, "ie-icon-in-${UUID.randomUUID()}").also { it.mkdirs() }
        try {
            unzip(zip, staging)
            val iconRoot = File(workDir, "icons/res/drawable-xxhdpi").also { it.mkdirs() }
            val files = staging.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in imageExt }
                .sortedBy { it.name }
                .toList()
            files.forEachIndexed { index, file ->
                val suffix = if (index == 0) "" else "_$index"
                val dest = File(iconRoot, "$packageName$suffix.${file.extension}")
                if (dest.exists()) dest.delete()
                file.copyTo(dest, overwrite = true)
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    fun packMeta(projectDir: File, workDir: File, destination: File) {
        val staging = File(destination.parentFile, "ie-meta-${UUID.randomUUID()}").also { it.mkdirs() }
        try {
            for (name in listOf("metadata.json", "preferences.json", "icon_mapping.json")) {
                val src = File(projectDir, name)
                if (src.isFile) src.copyTo(File(staging, name), overwrite = true)
            }
            val assetRelPaths = listOf(
                ApkPackAssets.LAUNCHER_ICON_PATH,
                ApkPackAssets.MaskLayer.Back.relativePath,
                ApkPackAssets.MaskLayer.Mask.relativePath,
                ApkPackAssets.MaskLayer.Upon.relativePath,
            )
            for (relative in assetRelPaths) {
                val src = File(workDir, relative)
                if (!src.isFile) continue
                val dest = File(staging, relative)
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
            }
            if (destination.exists()) destination.delete()
            zipDirectory(staging, destination)
        } finally {
            staging.deleteRecursively()
        }
    }

    fun applyMeta(zip: File, projectDir: File, workDir: File) {
        val staging = File(projectDir.parentFile ?: projectDir, "ie-meta-in-${UUID.randomUUID()}").also { it.mkdirs() }
        try {
            unzip(zip, staging)
            for (name in listOf("metadata.json", "preferences.json", "icon_mapping.json")) {
                val src = File(staging, name)
                if (!src.isFile) continue
                src.copyTo(File(projectDir, name), overwrite = true)
            }
            val assetRelPaths = listOf(
                ApkPackAssets.LAUNCHER_ICON_PATH,
                ApkPackAssets.MaskLayer.Back.relativePath,
                ApkPackAssets.MaskLayer.Mask.relativePath,
                ApkPackAssets.MaskLayer.Upon.relativePath,
            )
            for (relative in assetRelPaths) {
                val src = File(staging, relative)
                if (!src.isFile) continue
                val dest = File(workDir, relative)
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
            }
        } finally {
            staging.deleteRecursively()
        }
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
}
