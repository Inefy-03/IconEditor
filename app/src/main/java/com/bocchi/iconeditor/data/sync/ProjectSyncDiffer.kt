package com.bocchi.iconeditor.data.sync

import com.bocchi.iconeditor.data.ApkPackAssets
import com.bocchi.iconeditor.data.ArchiveService
import com.bocchi.iconeditor.model.IconAsset
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.model.ProjectSummary
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json

object ProjectSyncInventoryBuilder {
    private val json = Json { ignoreUnknownKeys = true }

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
        // 只比语义化项目信息；preferences / mapping 会随本地操作变化，容易误报。
        return ProjectSyncInventory(
            project = ProjectSyncProjectInfo(project.id, project.name, project.updatedAt),
            icons = iconEntries,
            assets = assets,
            metadataFingerprint = semanticMetadataFingerprint(metadataFile),
        )
    }

    fun fingerprint(variants: List<IconAsset>, workDir: File): String {
        // 只比图片内容，不依赖变体文件名（避免历史同步重命名导致误报不同）。
        val hashes = variants
            .map { fileFingerprint(File(workDir, it.archivePath)) }
            .filter { it.isNotEmpty() }
            .sorted()
        return sha256Hex(hashes.joinToString("\n").toByteArray(Charsets.UTF_8))
    }

    fun fileFingerprint(file: File): String {
        if (!file.isFile) return ""
        // 必须用内容哈希：同步写入后 mtime 会变，用时间戳会导致误报「内容不同」。
        return sha256Hex(file.readBytes())
    }

    fun semanticMetadataFingerprint(metadataFile: File): String {
        if (!metadataFile.isFile) return ""
        return runCatching {
            val meta = json.decodeFromString(ProjectMetadata.serializer(), metadataFile.readText())
            sha256Hex(canonicalMetadata(meta).toByteArray(Charsets.UTF_8))
        }.getOrElse { fileFingerprint(metadataFile) }
    }

    fun canonicalMetadata(meta: ProjectMetadata): String = buildString {
        val apk = meta.apk
        append("apk|").append(apk.packageName.trim()).append('|')
            .append(apk.versionName.trim()).append('|')
            .append(apk.versionCode).append('|')
            .append(apk.label.trim()).append('|')
            .append(apk.author.trim()).append('\n')
        val mtz = meta.mtz
        append("mtz|").append(mtz.version.trim()).append('|')
            .append(mtz.author.trim()).append('|')
            .append(mtz.designer.trim()).append('|')
            .append(mtz.title.trim()).append('\n')
        val module = meta.module
        append("module|").append(module.id.trim()).append('|')
            .append(module.name.trim()).append('|')
            .append(module.author.trim()).append('|')
            .append(module.version.trim()).append('|')
            .append(module.theme.trim()).append('|')
            .append(module.themeId.trim())
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

        val metaDiff = local.metadataFingerprint != remote.metadataFingerprint
        val assetDiff = local.assets != remote.assets
        if (metaDiff || assetDiff) {
            val detail = when {
                metaDiff && assetDiff -> "项目信息与遮罩/启动器不同"
                metaDiff -> "项目信息不同"
                else -> "遮罩或启动器图标不同"
            }
            items += ProjectSyncDiffItem(
                id = "metadata",
                kind = ProjectSyncKind.metadataChanged,
                packageName = "",
                appName = "项目信息 / 遮罩 / 启动器图标",
                detail = detail,
                selected = false,
                action = ProjectSyncAction.pullToLocal,
                isDeletionChoice = false,
            )
        }

        items.sortWith(
            compareBy<ProjectSyncDiffItem> { kindPriority(it.kind) }
                .thenBy { it.appName.lowercase() }
                .thenBy { it.packageName },
        )

        return ProjectSyncDiffPreview(
            projectId = local.project.id.ifBlank { remote.project.id },
            projectName = local.project.name.ifBlank { remote.project.name },
            items = items,
        )
    }

    private fun kindPriority(kind: ProjectSyncKind): Int = when (kind) {
        ProjectSyncKind.bothChanged -> 0
        ProjectSyncKind.metadataChanged -> 1
        ProjectSyncKind.missingOnRemote, ProjectSyncKind.localOnly -> 2
        ProjectSyncKind.missingOnLocal, ProjectSyncKind.remoteOnly -> 3
        else -> 4
    }
}
