package com.bocchi.iconeditor.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.bocchi.iconeditor.model.SourceType
import java.util.zip.ZipInputStream

object ImportSourceDetector {
    fun detect(context: Context, uri: Uri, displayName: String): SourceType {
        detectFromFileName(displayName)?.let { return it }
        detectFromMime(context.contentResolver.getType(uri))?.let { return it }
        return detectFromArchive(context, uri) ?: throw InvalidProjectArchiveException()
    }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        val queried = queryDisplayName(context, uri)
        if (queried.isNotBlank() && queried.contains('.')) return queried
        val mime = context.contentResolver.getType(uri)
        val extension = when {
            mime == "application/vnd.android.package-archive" -> "apk"
            mime == "application/x-miui-theme" -> "mtz"
            mime == "application/zip" || mime == "application/x-zip-compressed" -> "zip"
            else -> null
        }
        if (extension != null) {
            val base = queried.substringBeforeLast('.').ifBlank { "imported" }
            return "$base.$extension"
        }
        val path = uri.lastPathSegment.orEmpty()
        if (path.contains('.')) return path.substringAfterLast('/')
        return queried.ifBlank { path.ifBlank { "imported.zip" } }
    }

    private fun detectFromFileName(displayName: String): SourceType? = when (
        displayName.substringAfterLast('.', "").lowercase()
    ) {
        "apk" -> SourceType.Apk
        "mtz" -> SourceType.Mtz
        "zip" -> null
        else -> null
    }

    private fun detectFromMime(mime: String?): SourceType? = when (mime) {
        "application/vnd.android.package-archive" -> SourceType.Apk
        "application/x-miui-theme" -> SourceType.Mtz
        else -> null
    }

    private fun detectFromArchive(context: Context, uri: Uri): SourceType? {
        val headers = readZipEntryNames(context, uri)
        if (headers.isEmpty()) return null
        val normalized = headers.map { it.trimStart('/') }.toSet()
        if (normalized.any { it == "AndroidManifest.xml" }) {
            return SourceType.Apk
        }
        if (normalized.contains("description.xml")) {
            return SourceType.Mtz
        }
        if (normalized.contains("icons")) {
            return SourceType.Module
        }
        if (normalized.any { it == "assets/appfilter.xml" || it.endsWith("/appfilter.xml") }) {
            return SourceType.Apk
        }
        return null
    }

    private fun readZipEntryNames(context: Context, uri: Uri): Set<String> {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    buildSet {
                        var entry = zip.nextEntry
                        var count = 0
                        while (entry != null && count < 256) {
                            add(entry.name)
                            zip.closeEntry()
                            entry = zip.nextEntry
                            count++
                        }
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptySet())
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
                }.orEmpty()
        }.getOrDefault("")
    }
}
