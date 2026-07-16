package com.bocchi.iconeditor.data.sync

import com.bocchi.iconeditor.data.ApkPackAssets
import com.bocchi.iconeditor.data.ArchiveService
import com.bocchi.iconeditor.model.IconAsset
import com.bocchi.iconeditor.model.ProjectSummary
import java.io.File
import java.security.MessageDigest

object ProjectSyncInventoryBuilder {
    fun build(
        project: ProjectSummary,
        workDir: File,
        metadataFile: File,
        mappingFile: File,
        preferencesFile: File,
        resolveAppName: (String) -> String,
    ): ProjectSyncInventory {
        val icons = ArchiveService.scanIconAssetsLite(workDir)
        val grouped = icons.groupBy { it.packageName }
        val iconEntries = grouped.keys.sorted().map { packageName ->
            val variants = grouped[packageName].orEmpty()
            ProjectSyncIconEntry(
                packageName = packageName,
                appName = resolveAppName(packageName),
                fingerprint = fingerprint(variants, workDir),
                variantCount = variants.size,
            )
        }
        val assets = ProjectSyncAssetsFingerprint(
            launcher = fileFingerprint(File(workDir, ApkPackAssets.LAUNCHER_ICON_PATH)),
            iconback = fileFingerprint(File(workDir, ApkPackAssets.MaskLayer.Back.relativePath)),
            iconmask = fileFingerprint(File(workDir, ApkPackAssets.MaskLayer.Mask.relativePath)),
            iconupon = fileFingerprint(File(workDir, ApkPackAssets.MaskLayer.Upon.relativePath)),
        )
        val metaParts = listOf(
            fileFingerprint(metadataFile),
            fileFingerprint(mappingFile),
            fileFingerprint(preferencesFile),
        ).joinToString("|")
        return ProjectSyncInventory(
            project = ProjectSyncProjectInfo(project.id, project.name, project.updatedAt),
            icons = iconEntries,
            assets = assets,
            metadataFingerprint = sha256Hex(metaParts.toByteArray(Charsets.UTF_8)),
        )
    }

    fun fingerprint(variants: List<IconAsset>, workDir: File): String {
        val parts = variants
            .sortedBy { it.variantKey }
            .map { asset ->
                val file = File(workDir, asset.archivePath)
                "${asset.variantKey}:${fileFingerprint(file)}"
            }
        return sha256Hex(parts.joinToString("\n").toByteArray(Charsets.UTF_8))
    }

    fun fileFingerprint(file: File): String {
        if (!file.isFile) return ""
        return "${file.length()}:${file.lastModified() / 1000}"
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object ProjectSyncDiffer {
    fun diff(local: ProjectSyncInventory, remote: ProjectSyncInventory): ProjectSyncDiffPreview {
        val localMap = local.icons.associateBy { it.packageName }
        val remoteMap = remote.icons.associateBy { it.packageName }
        val allPackages = (localMap.keys + remoteMap.keys).sorted()
        val items = mutableListOf<ProjectSyncDiffItem>()

        for (packageName in allPackages) {
            val loc = localMap[packageName]
            val rem = remoteMap[packageName]
            when {
                loc == null && rem != null -> items += ProjectSyncDiffItem(
                    id = "icon-$packageName",
                    kind = ProjectSyncKind.missingOnLocal,
                    packageName = packageName,
                    appName = rem.appName,
                    detail = "仅对方有 · 请选择：对方新增 或 本机已删",
                    selected = false,
                    action = ProjectSyncAction.pullToLocal,
                    isDeletionChoice = true,
                )
                loc != null && rem == null -> items += ProjectSyncDiffItem(
                    id = "icon-$packageName",
                    kind = ProjectSyncKind.missingOnRemote,
                    packageName = packageName,
                    appName = loc.appName,
                    detail = "仅本机有 · 请选择：本机新增 或 对方已删",
                    selected = false,
                    action = ProjectSyncAction.pushToRemote,
                    isDeletionChoice = true,
                )
                loc != null && rem != null && loc.fingerprint != rem.fingerprint -> items += ProjectSyncDiffItem(
                    id = "icon-$packageName",
                    kind = ProjectSyncKind.bothChanged,
                    packageName = packageName,
                    appName = loc.appName.ifBlank { rem.appName },
                    detail = "双方均有且内容不同",
                    selected = false,
                    action = ProjectSyncAction.pullToLocal,
                    isDeletionChoice = false,
                )
            }
        }

        if (local.metadataFingerprint != remote.metadataFingerprint || local.assets != remote.assets) {
            items.add(
                0,
                ProjectSyncDiffItem(
                    id = "metadata",
                    kind = ProjectSyncKind.metadataChanged,
                    packageName = "",
                    appName = "项目信息 / 遮罩 / 启动器图标",
                    detail = "元数据或 APK 资源不同",
                    selected = false,
                    action = ProjectSyncAction.pullToLocal,
                    isDeletionChoice = false,
                ),
            )
        }

        return ProjectSyncDiffPreview(
            projectId = local.project.id.ifBlank { remote.project.id },
            projectName = local.project.name.ifBlank { remote.project.name },
            items = items,
        )
    }
}
