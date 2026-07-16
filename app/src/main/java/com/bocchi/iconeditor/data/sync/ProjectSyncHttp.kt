package com.bocchi.iconeditor.data.sync

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ProjectSyncHttpServer(
    private val port: Int = ProjectSyncConstants.DEFAULT_PORT,
    val token: String,
    private val handler: (ProjectSyncHttpRequest) -> ProjectSyncHttpResponse,
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    val isRunning: Boolean get() = running.get()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(port))
        serverSocket = socket
        executor.execute {
            while (running.get()) {
                try {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                val input = BufferedInputStream(s.getInputStream())
                val request = ProjectSyncHttpRequest.parse(input)
                val response = if (
                    request.token == token ||
                    request.path == "/v1/health" ||
                    request.path.startsWith("/v1/health?")
                ) {
                    handler(request)
                } else {
                    ProjectSyncHttpResponse(401, body = "unauthorized".toByteArray())
                }
                response.writeTo(s.getOutputStream())
            } catch (e: Exception) {
                runCatching {
                    ProjectSyncHttpResponse(500, body = (e.message ?: "error").toByteArray())
                        .writeTo(s.getOutputStream())
                }
            }
        }
    }
}

data class ProjectSyncHttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    val token: String
        get() = headers[ProjectSyncConstants.TOKEN_HEADER.lowercase()]
            ?: query["token"]
            ?: ""

    companion object {
        fun parse(input: InputStream): ProjectSyncHttpRequest {
            val headerBytes = ByteArrayOutputStream()
            var match = 0
            val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
            while (match < 4) {
                val b = input.read()
                if (b < 0) break
                headerBytes.write(b)
                if (b == end[match].toInt() and 0xff) {
                    match++
                } else {
                    match = if (b == end[0].toInt() and 0xff) 1 else 0
                }
            }
            val headerText = headerBytes.toString(StandardCharsets.UTF_8.name())
            val lines = headerText.trimEnd().split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty()
            val parts = requestLine.split(" ")
            require(parts.size >= 2) { "无效请求行" }
            val method = parts[0]
            val pathQuery = parts[1]
            val pathParts = pathQuery.split("?", limit = 2)
            val path = pathParts[0]
            val query = mutableMapOf<String, String>()
            if (pathParts.size > 1) {
                for (pair in pathParts[1].split("&")) {
                    val kv = pair.split("=", limit = 2)
                    if (kv.size == 2) query[kv[0]] = java.net.URLDecoder.decode(kv[1], "UTF-8")
                }
            }
            val headers = mutableMapOf<String, String>()
            for (line in lines.drop(1)) {
                if (line.isEmpty()) break
                val kv = line.split(":", limit = 2)
                if (kv.size == 2) {
                    headers[kv[0].trim().lowercase()] = kv[1].trim()
                }
            }
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                input.readNBytes(contentLength)
            } else {
                ByteArray(0)
            }
            return ProjectSyncHttpRequest(method, path, query, headers, body)
        }
    }
}

data class ProjectSyncHttpResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
) {
    fun writeTo(output: OutputStream) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        val out = BufferedOutputStream(output)
        val hdrs = headers.toMutableMap()
        hdrs["Content-Length"] = body.size.toString()
        hdrs["Connection"] = "close"
        out.write("HTTP/1.1 $status $reason\r\n".toByteArray())
        for ((k, v) in hdrs) {
            out.write("$k: $v\r\n".toByteArray())
        }
        out.write("\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    companion object {
        fun json(status: Int, payload: String): ProjectSyncHttpResponse =
            ProjectSyncHttpResponse(
                status,
                mapOf("Content-Type" to "application/json; charset=utf-8"),
                payload.toByteArray(Charsets.UTF_8),
            )
    }
}

class ProjectSyncClient(
    private val baseUrl: String,
    private val token: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun health(): Boolean {
        val (code, _) = request("GET", "/v1/health")
        return code == 200
    }

    fun listProjects(): List<com.bocchi.iconeditor.model.ProjectSummary> {
        val (code, body) = request("GET", "/v1/projects")
        require(code == 200) { "请求失败：/v1/projects" }
        return json.decodeFromString(
            ListSerializer(com.bocchi.iconeditor.model.ProjectSummary.serializer()),
            body.decodeToString(),
        )
    }

    fun ensureProject(summary: com.bocchi.iconeditor.model.ProjectSummary): com.bocchi.iconeditor.model.ProjectSummary {
        val payload = json.encodeToString(
            com.bocchi.iconeditor.model.ProjectSummary.serializer(),
            summary,
        ).toByteArray(StandardCharsets.UTF_8)
        val (code, body) = request(
            "POST",
            "/v1/projects",
            payload,
            "application/json; charset=utf-8",
        )
        require(code == 200) { "在对方新建项目失败" }
        return json.decodeFromString(
            com.bocchi.iconeditor.model.ProjectSummary.serializer(),
            body.decodeToString(),
        )
    }

    fun inventory(projectId: String): ProjectSyncInventory {
        val (code, body) = request("GET", "/v1/projects/$projectId/inventory")
        require(code == 200) { "请求失败：inventory" }
        return json.decodeFromString(ProjectSyncInventory.serializer(), body.decodeToString())
    }

    fun downloadIconPackage(projectId: String, packageName: String, file: java.io.File) {
        val encoded = java.net.URLEncoder.encode(packageName, "UTF-8").replace("+", "%20")
        val (code, body) = request("GET", "/v1/projects/$projectId/icons/$encoded")
        require(code == 200) { "下载图标失败：$packageName" }
        file.writeBytes(body)
    }

    fun downloadIconPackageBytes(projectId: String, packageName: String): ByteArray {
        val encoded = java.net.URLEncoder.encode(packageName, "UTF-8").replace("+", "%20")
        val (code, body) = request("GET", "/v1/projects/$projectId/icons/$encoded")
        require(code == 200) { "下载图标失败：$packageName" }
        return body
    }

    fun uploadIconPackage(projectId: String, packageName: String, file: java.io.File) {
        uploadIconPackageBytes(projectId, packageName, file.readBytes())
    }

    fun uploadIconPackageBytes(projectId: String, packageName: String, data: ByteArray) {
        val encoded = java.net.URLEncoder.encode(packageName, "UTF-8").replace("+", "%20")
        val (code, _) = request(
            "PUT",
            "/v1/projects/$projectId/icons/$encoded",
            data,
            ProjectSyncBundle.CONTENT_TYPE,
        )
        require(code == 200) { "上传图标失败：$packageName" }
    }

    fun deleteIconPackage(projectId: String, packageName: String) {
        val encoded = java.net.URLEncoder.encode(packageName, "UTF-8").replace("+", "%20")
        val (code, _) = request("DELETE", "/v1/projects/$projectId/icons/$encoded")
        require(code == 200) { "删除远端图标失败：$packageName" }
    }

    fun pullMetadataPack(projectId: String, file: java.io.File) {
        file.writeBytes(pullMetadataBytes(projectId))
    }

    fun pullMetadataBytes(projectId: String): ByteArray {
        val (code, body) = request("GET", "/v1/projects/$projectId/meta")
        require(code == 200) { "下载元数据失败" }
        return body
    }

    fun pushMetadataPack(projectId: String, file: java.io.File) {
        pushMetadataBytes(projectId, file.readBytes())
    }

    fun pushMetadataBytes(projectId: String, data: ByteArray) {
        val (code, _) = request(
            "PUT",
            "/v1/projects/$projectId/meta",
            data,
            ProjectSyncBundle.CONTENT_TYPE,
        )
        require(code == 200) { "上传元数据失败" }
    }

    fun registerPeer(announce: ProjectSyncPeerAnnounce) {
        val body = json.encodeToString(ProjectSyncPeerAnnounce.serializer(), announce)
            .toByteArray(StandardCharsets.UTF_8)
        val (code, _) = request(
            "POST",
            "/v1/pair",
            body,
            "application/json; charset=utf-8",
        )
        require(code == 200) { "互相配对失败" }
    }

    fun finalizeProject(projectId: String) {
        val (code, _) = request("POST", "/v1/projects/$projectId/finalize")
        require(code == 200) { "同步收尾失败" }
    }

    private fun request(
        method: String,
        path: String,
        body: ByteArray = ByteArray(0),
        contentType: String? = null,
    ): Pair<Int, ByteArray> {
        val url = java.net.URL(baseUrl.trimEnd('/') + path)
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty(ProjectSyncConstants.TOKEN_HEADER, token)
            doInput = true
            if (body.isNotEmpty()) {
                doOutput = true
                contentType?.let { setRequestProperty("Content-Type", it) }
                setRequestProperty("Content-Length", body.size.toString())
            }
        }
        if (body.isNotEmpty()) {
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        conn.disconnect()
        return code to bytes
    }
}
