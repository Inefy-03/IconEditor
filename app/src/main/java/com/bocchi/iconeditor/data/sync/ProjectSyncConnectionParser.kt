package com.bocchi.iconeditor.data.sync

import android.net.Uri

data class ProjectSyncConnection(
    val host: String,
    val port: Int,
    val token: String,
)

object ProjectSyncConnectionParser {
    fun parse(raw: String): ProjectSyncConnection? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        // iconeditor-sync://connect?host=...&port=...&token=...
        val uri = Uri.parse(text)
        if (uri.scheme.equals("iconeditor-sync", ignoreCase = true)) {
            val host = uri.getQueryParameter("host")?.trim().orEmpty()
            val token = uri.getQueryParameter("token")?.trim().orEmpty()
            val port = uri.getQueryParameter("port")?.toIntOrNull()
                ?: ProjectSyncConstants.DEFAULT_PORT
            if (host.isNotEmpty() && token.isNotEmpty()) {
                return ProjectSyncConnection(host, port, token)
            }
        }

        // Fallback: http://host:port/?token=... or host:port|token
        if (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            val host = uri.host?.trim().orEmpty()
            val port = if (uri.port != -1) uri.port else ProjectSyncConstants.DEFAULT_PORT
            val token = uri.getQueryParameter("token")?.trim().orEmpty()
            if (host.isNotEmpty() && token.isNotEmpty()) {
                return ProjectSyncConnection(host, port, token)
            }
        }

        val pipe = text.split('|', limit = 2)
        if (pipe.size == 2) {
            val left = pipe[0].trim()
            val token = pipe[1].trim()
            val hostPort = left.split(':', limit = 2)
            if (token.isNotEmpty() && hostPort.isNotEmpty()) {
                val host = hostPort[0].trim()
                val port = hostPort.getOrNull(1)?.toIntOrNull() ?: ProjectSyncConstants.DEFAULT_PORT
                if (host.isNotEmpty()) return ProjectSyncConnection(host, port, token)
            }
        }
        return null
    }
}
