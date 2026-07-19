package com.bocchi.iconeditor.data.sync

import kotlinx.serialization.Serializable

@Serializable
enum class ProjectSyncKind {
    remoteOnly,
    localOnly,
    bothChanged,
    identical,
    missingOnRemote,
    missingOnLocal,
    metadataChanged,
}

@Serializable
enum class ProjectSyncAction {
    pullToLocal,
    pushToRemote,
    deleteLocal,
    deleteRemote,
    skip,
}

@Serializable
data class ProjectSyncIconEntry(
    val packageName: String,
    val appName: String,
    val fingerprint: String,
    val variantCount: Int,
)

@Serializable
data class ProjectSyncAssetsFingerprint(
    val launcher: String = "",
    val iconback: String = "",
    val iconmask: String = "",
    val iconupon: String = "",
)

@Serializable
data class ProjectSyncProjectInfo(
    val id: String,
    val name: String,
    val updatedAt: Long,
)

@Serializable
data class ProjectSyncInventory(
    val schemaVersion: Int = 1,
    val project: ProjectSyncProjectInfo,
    val icons: List<ProjectSyncIconEntry>,
    val assets: ProjectSyncAssetsFingerprint,
    val metadataFingerprint: String,
)

@Serializable
data class ProjectSyncDiffItem(
    val id: String,
    val kind: ProjectSyncKind,
    val packageName: String,
    val appName: String,
    val detail: String,
    val selected: Boolean,
    val action: ProjectSyncAction,
    val isDeletionChoice: Boolean,
)

data class ProjectSyncDiffPreview(
    val projectId: String,
    val projectName: String,
    val items: List<ProjectSyncDiffItem>,
) {
    val selectedCount: Int get() = items.count { it.selected }
}

object ProjectSyncConstants {
    const val DEFAULT_PORT = 18765
    const val TOKEN_HEADER = "X-IconEditor-Token"
    const val ADB_REMOTE_DIRECTORY = "/sdcard/Download/IconEditorSync"
}

/** Reciprocal pairing payload so the host also learns how to dial the client. */
@Serializable
data class ProjectSyncPeerAnnounce(
    val host: String,
    val port: Int = ProjectSyncConstants.DEFAULT_PORT,
    val token: String,
)
