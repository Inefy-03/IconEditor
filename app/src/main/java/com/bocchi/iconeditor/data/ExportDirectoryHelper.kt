package com.bocchi.iconeditor.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
        val relativePath = runCatching {
            val documentId = DocumentsContract.getTreeDocumentId(custom)
            documentId.substringAfter(':', documentId)
        }.getOrDefault(custom.lastPathSegment.orEmpty())
            .substringAfter(':')
            .trim('/')
        return if (relativePath.isBlank()) "/" else "/$relativePath"
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
        documentRelativePath(uri)?.let { return "/$it" }
        queryMediaRelativePath(context.contentResolver, uri)?.let { return "/$it" }
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

    /** Opens the actual parent directory when the exported document exposes one. */
    fun openExportDirectory(
        context: Context,
        settings: AppSettings,
        exportedUri: Uri? = null,
    ): Boolean {
        val customTree = settings.exportDirectoryUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val folderUri = exportedUri?.let(::parentDocumentUri) ?: if (customTree != null) {
            DocumentsContract.buildDocumentUriUsingTree(
                customTree,
                DocumentsContract.getTreeDocumentId(customTree),
            )
        } else {
            defaultDownloadsDocumentUri
        }
        if (startDirectoryViewer(context, folderUri, XiaomiFileManagerPackage)) return true

        val baseViewIntent = directoryViewIntent(folderUri)
        val viewerPackages = context.packageManager
            .queryIntentActivities(baseViewIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .map { it.activityInfo.packageName }
            .filterNot { it.contains("downloads", ignoreCase = true) }
            .distinct()
            .toList()
        viewerPackages.forEach { packageName ->
            if (startDirectoryViewer(context, folderUri, packageName)) return true
        }

        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.isSuccess
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

    private fun queryMediaRelativePath(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty().trim('/') else ""
            val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
            listOf(path, name).filter { it.isNotBlank() }.joinToString("/").takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun documentRelativePath(uri: Uri): String? = runCatching {
        if (uri.authority != ExternalStorageDocumentsAuthority) return@runCatching null
        DocumentsContract.getDocumentId(uri)
            .substringAfter(':')
            .removePrefix("/storage/emulated/0/")
            .trim('/')
            .takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun parentDocumentUri(uri: Uri): Uri? = runCatching {
        val authority = uri.authority ?: return@runCatching null
        if (DocumentsContract.isTreeUri(uri)) {
            return@runCatching DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri),
            )
        }
        if (authority != ExternalStorageDocumentsAuthority) return@runCatching null
        val documentId = DocumentsContract.getDocumentId(uri)
        val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = documentId)
        if (parentId == documentId) return@runCatching null
        DocumentsContract.buildDocumentUri(authority, parentId)
    }.getOrNull()

    private fun directoryViewIntent(folderUri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun startDirectoryViewer(context: Context, folderUri: Uri, packageName: String): Boolean {
        val intent = directoryViewIntent(folderUri).setPackage(packageName)
        if (context.packageManager.resolveActivity(intent, 0) == null) return false
        return runCatching { context.startActivity(intent) }.isSuccess
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

    private const val XiaomiFileManagerPackage = "com.android.fileexplorer"
    private const val ExternalStorageDocumentsAuthority = "com.android.externalstorage.documents"
}
