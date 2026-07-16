package com.bocchi.iconeditor.data.sync

import com.bocchi.iconeditor.data.ProjectRepository
import com.bocchi.iconeditor.model.ProjectSummary
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

class ProjectSyncRouteHandler(
    private val repository: ProjectRepository,
    private val resolveAppName: (String) -> String,
    private val cacheDir: File,
    private val onPeerPaired: ((ProjectSyncPeerAnnounce) -> Unit)? = null,
    private val onProjectEnsured: ((ProjectSummary) -> Unit)? = null,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val lock = Any()

    fun handle(request: ProjectSyncHttpRequest): ProjectSyncHttpResponse {
        val method = request.method.uppercase()
        val path = request.path

        if (method == "GET" && (path == "/v1/health" || path.startsWith("/v1/health?"))) {
            return ProjectSyncHttpResponse.json(200, """{"ok":true}""")
        }
        if (method == "POST" && (path == "/v1/pair" || path.startsWith("/v1/pair?"))) {
            val announce = runCatching {
                json.decodeFromString(ProjectSyncPeerAnnounce.serializer(), request.body.decodeToString())
            }.getOrNull()
            if (announce == null || announce.host.isBlank() || announce.token.isBlank()) {
                return ProjectSyncHttpResponse(400, body = "invalid peer".toByteArray())
            }
            onPeerPaired?.invoke(announce)
            return ProjectSyncHttpResponse.json(200, """{"ok":true}""")
        }

        val parts = path.trim('/').split('/')

        // Icon GET/PUT: transfer raw image bytes (no zip). Concurrent OK — different packages.
        if (parts.size >= 5 && parts[0] == "v1" && parts[1] == "projects" && parts[3] == "icons"
            && (method == "GET" || method == "PUT")
        ) {
            val projectId = java.net.URLDecoder.decode(parts[2], "UTF-8")
            val packageName = java.net.URLDecoder.decode(parts.drop(4).joinToString("/"), "UTF-8")
            if (method == "GET") {
                val body = ProjectSyncPackager.packIconPackageBytes(
                    repository.workDirectory(projectId),
                    packageName,
                )
                return ProjectSyncHttpResponse(
                    200,
                    mapOf("Content-Type" to ProjectSyncBundle.CONTENT_TYPE),
                    body,
                )
            }
            repository.applySyncIconBytes(projectId, packageName, request.body, rebuildMapping = false)
            return ProjectSyncHttpResponse.json(200, """{"ok":true}""")
        }

        return synchronized(lock) {
            handleLocked(method, path, parts, request)
        }
    }

    private fun handleLocked(
        method: String,
        path: String,
        parts: List<String>,
        request: ProjectSyncHttpRequest,
    ): ProjectSyncHttpResponse {
        if (method == "GET" && (path == "/v1/projects" || path.startsWith("/v1/projects?"))) {
            val projects = repository.loadProjects()
            return ProjectSyncHttpResponse.json(
                200,
                json.encodeToString(ListSerializer(ProjectSummary.serializer()), projects),
            )
        }
        if (method == "POST" && (path == "/v1/projects" || path.startsWith("/v1/projects?"))) {
            val summary = runCatching {
                json.decodeFromString(ProjectSummary.serializer(), request.body.decodeToString())
            }.getOrNull()
            if (summary == null || summary.id.isBlank()) {
                return ProjectSyncHttpResponse(400, body = "invalid project".toByteArray())
            }
            val ensured = repository.ensureProject(summary)
            onProjectEnsured?.invoke(ensured)
            return ProjectSyncHttpResponse.json(
                200,
                json.encodeToString(ProjectSummary.serializer(), ensured),
            )
        }

        if (parts.size < 3 || parts[0] != "v1" || parts[1] != "projects") {
            return ProjectSyncHttpResponse(404, body = "not found".toByteArray())
        }
        val projectId = java.net.URLDecoder.decode(parts[2], "UTF-8")

        if (parts.size == 4 && parts[3] == "finalize" && method == "POST") {
            repository.finalizeSyncProject(projectId)
            return ProjectSyncHttpResponse.json(200, """{"ok":true}""")
        }

        if (parts.size == 4 && parts[3] == "inventory" && method == "GET") {
            val inventory = repository.buildSyncInventory(projectId, resolveAppName)
            return ProjectSyncHttpResponse.json(200, json.encodeToString(inventory))
        }

        if (parts.size == 4 && parts[3] == "pack" && method == "GET") {
            val zip = File(cacheDir, "$projectId.ieproj.zip")
            repository.packSyncProject(projectId, resolveAppName, zip)
            val data = zip.readBytes()
            zip.delete()
            return ProjectSyncHttpResponse(
                200,
                mapOf("Content-Type" to "application/zip"),
                data,
            )
        }

        if (parts.size == 4 && parts[3] == "meta") {
            if (method == "GET") {
                val body = ProjectSyncPackager.packMetaBytes(
                    repository.projectDirectory(projectId),
                    repository.workDirectory(projectId),
                )
                return ProjectSyncHttpResponse(
                    200,
                    mapOf("Content-Type" to ProjectSyncBundle.CONTENT_TYPE),
                    body,
                )
            }
            if (method == "PUT") {
                repository.applySyncMetaBytes(projectId, request.body)
                return ProjectSyncHttpResponse.json(200, """{"ok":true}""")
            }
        }

        if (parts.size >= 5 && parts[3] == "icons") {
            val packageName = java.net.URLDecoder.decode(parts.drop(4).joinToString("/"), "UTF-8")
            if (method == "DELETE") {
                repository.deleteSyncIconPackage(projectId, packageName)
                return ProjectSyncHttpResponse.json(200, """{"ok":true}""")
            }
        }

        return ProjectSyncHttpResponse(404, body = "not found".toByteArray())
    }
}

    object ProjectSyncOrchestrator {
    const val APPLY_CONCURRENCY = 32

    fun applySelected(
        preview: ProjectSyncDiffPreview,
        client: ProjectSyncClient,
        repository: ProjectRepository,
        projectId: String,
        cacheDir: File,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ) {
        val selected = preview.items.filter { it.selected && it.action != ProjectSyncAction.skip }
        if (selected.isEmpty()) return
        val total = selected.size
        val progressLock = Any()
        var done = 0
        fun report(label: String) {
            val current = synchronized(progressLock) { done }
            onProgress(current, total, label)
        }
        fun advance() {
            val current = synchronized(progressLock) {
                done += 1
                done
            }
            onProgress(current, total, "")
        }

        val metadata = selected.filter { it.kind == ProjectSyncKind.metadataChanged }
        val network = selected.filter {
            it.kind != ProjectSyncKind.metadataChanged &&
                it.action in setOf(
                    ProjectSyncAction.pullToLocal,
                    ProjectSyncAction.pushToRemote,
                    ProjectSyncAction.deleteRemote,
                )
        }
        val localDeletes = selected.filter {
            it.kind != ProjectSyncKind.metadataChanged && it.action == ProjectSyncAction.deleteLocal
        }

        for (item in metadata) {
            report(item.appName.ifBlank { "项目元数据" })
            applyMetadata(item, client, repository, projectId, cacheDir)
            advance()
        }

        if (network.isNotEmpty()) {
            val pool = java.util.concurrent.Executors.newFixedThreadPool(
                APPLY_CONCURRENCY.coerceAtMost(network.size),
            )
            try {
                val futures = network.map { item ->
                    pool.submit {
                        report(item.appName.ifBlank { item.packageName })
                        applyIconNetwork(item, client, repository, projectId, cacheDir)
                        advance()
                    }
                }
                futures.forEach { it.get() }
            } finally {
                pool.shutdown()
            }
        }

        if (localDeletes.isNotEmpty()) {
            val packages = localDeletes.map { it.packageName }.filter { it.isNotBlank() }.toSet()
            val work = repository.workDirectory(projectId)
            for (item in localDeletes) {
                report(item.appName.ifBlank { item.packageName })
                com.bocchi.iconeditor.data.ArchiveService.deleteIconFiles(work, item.packageName)
                advance()
            }
            repository.deleteSyncIconPackagesBatch(projectId, packages)
        }

        runCatching { client.finalizeProject(projectId) }
        onProgress(total, total, "完成")
    }

    private fun applyMetadata(
        item: ProjectSyncDiffItem,
        client: ProjectSyncClient,
        repository: ProjectRepository,
        projectId: String,
        cacheDir: File,
    ) {
        when (item.action) {
            ProjectSyncAction.pullToLocal -> {
                repository.applySyncMetaBytes(projectId, client.pullMetadataBytes(projectId))
            }
            ProjectSyncAction.pushToRemote -> {
                client.pushMetadataBytes(
                    projectId,
                    ProjectSyncPackager.packMetaBytes(
                        repository.projectDirectory(projectId),
                        repository.workDirectory(projectId),
                    ),
                )
            }
            else -> Unit
        }
    }

    private fun applyIconNetwork(
        item: ProjectSyncDiffItem,
        client: ProjectSyncClient,
        repository: ProjectRepository,
        projectId: String,
        cacheDir: File,
    ) {
        val packageName = item.packageName
        if (packageName.isBlank()) return
        when (item.action) {
            ProjectSyncAction.pullToLocal -> {
                val data = client.downloadIconPackageBytes(projectId, packageName)
                repository.applySyncIconBytes(projectId, packageName, data, rebuildMapping = false)
            }
            ProjectSyncAction.pushToRemote -> {
                val data = ProjectSyncPackager.packIconPackageBytes(
                    repository.workDirectory(projectId),
                    packageName,
                )
                client.uploadIconPackageBytes(projectId, packageName, data)
            }
            ProjectSyncAction.deleteRemote -> client.deleteIconPackage(projectId, packageName)
            else -> Unit
        }
    }

    fun localIPv4Addresses(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return result
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (host !in result) result += host
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun preferredLanAddress(): String? {
        val all = localIPv4Addresses()
        return all.firstOrNull { it.startsWith("192.168.") }
            ?: all.firstOrNull { it.startsWith("10.") }
            ?: all.firstOrNull { it.startsWith("172.") }
            ?: all.firstOrNull()
    }
}
