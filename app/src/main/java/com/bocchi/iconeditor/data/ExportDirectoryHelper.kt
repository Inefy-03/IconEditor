package com.bocchi.iconeditor.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.AppSettings

data class ExportTarget(
    val uri: Uri,
    val displayName: String,
    val locationLabel: String,
)

object ExportDirectoryHelper {
    private val defaultDownloadsDocumentUri: Uri = Uri.parse(
        "content://com.android.externalstorage.documents/document/primary:Download",
    )
    private val defaultDownloadsTreeUri: Uri = Uri.parse(
        "content://com.android.externalstorage.documents/tree/primary%3ADownload",
    )

    fun defaultTreeInitialUri(): Uri = defaultDownloadsTreeUri

    fun initialDocumentUri(settings: AppSettings): Uri {
        val customTree = settings.exportDirectoryUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (customTree != null) {
            return DocumentsContract.buildDocumentUriUsingTree(
                customTree,
                DocumentsContract.getTreeDocumentId(customTree),
            )
        }
        return defaultDownloadsDocumentUri
    }

    fun displayLabel(context: Context, settings: AppSettings): String {
        val custom = settings.exportDirectoryUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: return context.getString(R.string.export_directory_download_default)
        return runCatching {
            val documentId = DocumentsContract.getTreeDocumentId(custom)
            documentId.substringAfter(':', documentId).ifBlank {
                context.getString(R.string.export_directory_download_default)
            }
        }.getOrDefault(custom.lastPathSegment.orEmpty())
            .ifBlank { context.getString(R.string.export_directory_download_default) }
    }

    fun createExportTarget(
        context: Context,
        settings: AppSettings,
        mimeType: String,
        displayName: String,
    ): ExportTarget? = runCatching {
        val customTree = settings.exportDirectoryUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val folderLabel = displayLabel(context, settings)
        if (customTree != null) {
            val uri = createInTree(context.contentResolver, customTree, mimeType, displayName)
            if (uri != null) {
                val name = queryDisplayName(context.contentResolver, uri).ifBlank { displayName }
                return@runCatching ExportTarget(
                    uri = uri,
                    displayName = name,
                    locationLabel = "$folderLabel/$name",
                )
            }
            // Tree URI invalid/inaccessible — fall back to MediaStore Downloads.
        }
        val result = createInDownloads(context.contentResolver, mimeType, displayName)
            ?: return@runCatching null
        ExportTarget(
            uri = result.uri,
            displayName = result.displayName,
            locationLabel = "${context.getString(R.string.export_directory_download_default)}/${result.displayName}",
        )
    }.getOrNull()

    fun describeExportLocation(context: Context, settings: AppSettings, uri: Uri, fallbackName: String): String {
        val folder = displayLabel(context, settings)
        val name = queryDisplayName(context.contentResolver, uri).ifBlank { fallbackName }
        return "$folder/$name"
    }

    fun finalizeExportIfNeeded(context: Context, uri: Uri) {
        if (!isDownloadsUri(uri)) return
        val values = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    fun abortPendingExport(context: Context, uri: Uri) {
        if (!isDownloadsUri(uri)) return
        runCatching {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun createInDownloads(
        resolver: ContentResolver,
        mimeType: String,
        displayName: String,
    ): DownloadInsertResult? {
        val uniqueName = ensureUniqueDownloadsName(resolver, displayName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return DownloadInsertResult(uri = uri, displayName = uniqueName)
    }

    private data class DownloadInsertResult(
        val uri: Uri,
        val displayName: String,
    )

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index).orEmpty()
                }
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun createInTree(
        resolver: ContentResolver,
        treeUri: Uri,
        mimeType: String,
        displayName: String,
    ): Uri? = runCatching {
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val existingNames = listTreeDocumentNames(resolver, treeUri)
        val uniqueName = ensureUniqueName(displayName, existingNames)
        DocumentsContract.createDocument(resolver, parentDocumentUri, mimeType, uniqueName)
    }.getOrNull()

    private fun ensureUniqueDownloadsName(resolver: ContentResolver, displayName: String): String {
        val existingNames = queryDownloadsDisplayNames(resolver)
        return ensureUniqueName(displayName, existingNames)
    }

    private fun queryDownloadsDisplayNames(resolver: ContentResolver): Set<String> {
        val names = mutableSetOf<String>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                names += cursor.getString(index)
            }
        }
        return names
    }

    private fun listTreeDocumentNames(resolver: ContentResolver, treeUri: Uri): Set<String> {
        val names = mutableSetOf<String>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                names += cursor.getString(index)
            }
        }
        return names
    }

    private fun ensureUniqueName(displayName: String, existingNames: Set<String>): String {
        if (displayName !in existingNames) return displayName
        val extension = displayName.substringAfterLast('.', "")
        val baseName = if (extension.isEmpty()) {
            displayName
        } else {
            displayName.removeSuffix(".$extension")
        }
        var counter = 1
        while (true) {
            val candidate = if (extension.isEmpty()) {
                "$baseName ($counter)"
            } else {
                "$baseName ($counter).$extension"
            }
            if (candidate !in existingNames) return candidate
            counter++
        }
    }

    private fun isDownloadsUri(uri: Uri): Boolean {
        return uri.authority == MediaStore.AUTHORITY &&
            uri.pathSegments.any { it == "downloads" }
    }
}
