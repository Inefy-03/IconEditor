package com.bocchi.iconeditor.ui.page

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.data.sync.ProjectSyncAction
import com.bocchi.iconeditor.data.sync.ProjectSyncDiffItem
import com.bocchi.iconeditor.data.sync.ProjectSyncDiffPreview
import com.bocchi.iconeditor.data.sync.ProjectSyncKind
import java.io.File
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun ProjectSyncPage(
    contentPadding: PaddingValues = PaddingValues(top = 12.dp, bottom = 12.dp),
    serverRunning: Boolean,
    serverPort: Int,
    serverToken: String,
    lanAddress: String?,
    peerHost: String,
    peerPort: String,
    peerToken: String,
    statusMessage: String?,
    onPeerHost: (String) -> Unit,
    onPeerPort: (String) -> Unit,
    onPeerToken: (String) -> Unit,
    onSavePeer: () -> Unit,
    onScanQr: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onProbe: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical(),
        contentPadding = contentPadding,
        overscrollEffect = null,
    ) {
        item { SmallTitle(stringResource(R.string.sync_section_host)) }
        item {
            PreferenceCard(bottom = 6.dp) {
                if (serverRunning) {
                    Text(
                        text = stringResource(
                            R.string.sync_server_running,
                            lanAddress ?: "0.0.0.0",
                            serverPort,
                        ),
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = stringResource(R.string.sync_token_label, serverToken),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.sync_stop_server),
                        onClick = onStopServer,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sync_host_hint),
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.sync_start_server),
                        onClick = onStartServer,
                    )
                }
            }
        }

        item { SmallTitle(stringResource(R.string.sync_section_connect)) }
        item {
            PreferenceCard(bottom = 6.dp) {
                Text(
                    text = stringResource(R.string.sync_connect_hint),
                    modifier = Modifier.padding(16.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                ArrowPreference(
                    title = stringResource(R.string.sync_scan_qr),
                    onClick = onScanQr,
                )
                TextField(
                    value = peerHost,
                    onValueChange = onPeerHost,
                    label = stringResource(R.string.sync_peer_host),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                TextField(
                    value = peerPort,
                    onValueChange = onPeerPort,
                    label = stringResource(R.string.sync_peer_port),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                TextField(
                    value = peerToken,
                    onValueChange = onPeerToken,
                    label = stringResource(R.string.sync_peer_token),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                ArrowPreference(
                    title = stringResource(R.string.sync_save_peer),
                    onClick = onSavePeer,
                )
                ArrowPreference(
                    title = stringResource(R.string.sync_probe),
                    onClick = onProbe,
                )
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            item {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
fun ProjectSyncDiffDialog(
    preview: ProjectSyncDiffPreview?,
    busy: Boolean,
    applyFraction: Float = 0f,
    applyTotal: Int = 0,
    applyStatus: String? = null,
    remoteThumbs: Map<String, ByteArray?>,
    localIconFile: (String) -> File?,
    onRequestRemoteThumb: (String) -> Unit,
    onToggle: (Int) -> Unit,
    onAction: (Int, ProjectSyncAction) -> Unit,
    onSelectPushLocal: () -> Unit,
    onSelectDeleteLocal: () -> Unit,
    onSelectPullRemote: () -> Unit,
    onSelectDeleteRemote: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    OverlayDialog(
        show = preview != null,
        title = stringResource(R.string.sync_diff_title),
        summary = preview?.let {
            stringResource(R.string.sync_diff_summary, it.projectName, it.items.size, it.selectedCount)
        },
        onDismissRequest = onDismiss,
    ) {
        val current = preview ?: return@OverlayDialog
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy && applyTotal > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SyncApplyProgressBar(fraction = applyFraction)
                    Text(
                        applyStatus ?: stringResource(R.string.sync_status_applying),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.subtitle,
                        maxLines = 1,
                    )
                }
            }
            Text(
                stringResource(
                    R.string.sync_only_local_hint,
                    current.items.count {
                        it.kind == ProjectSyncKind.missingOnRemote || it.kind == ProjectSyncKind.localOnly
                    },
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(modifier = Modifier.weight(1f), onClick = onSelectPushLocal) {
                    Text(stringResource(R.string.sync_select_local_added))
                }
                Button(modifier = Modifier.weight(1f), onClick = onSelectDeleteLocal) {
                    Text(stringResource(R.string.sync_select_peer_deleted))
                }
            }
            Text(
                stringResource(
                    R.string.sync_only_remote_hint,
                    current.items.count {
                        it.kind == ProjectSyncKind.missingOnLocal || it.kind == ProjectSyncKind.remoteOnly
                    },
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(modifier = Modifier.weight(1f), onClick = onSelectPullRemote) {
                    Text(stringResource(R.string.sync_select_remote_added))
                }
                Button(modifier = Modifier.weight(1f), onClick = onSelectDeleteRemote) {
                    Text(stringResource(R.string.sync_select_local_deleted))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(modifier = Modifier.weight(1f), onClick = onSelectAll) {
                    Text(stringResource(R.string.icon_import_select_all))
                }
                Button(modifier = Modifier.weight(1f), onClick = onSelectNone) {
                    Text(stringResource(R.string.icon_import_select_none))
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(current.items, key = { _, item -> item.id }) { index, item ->
                    SyncDiffRow(
                        item = item,
                        localFile = item.packageName.takeIf { it.isNotBlank() }?.let(localIconFile),
                        remoteBytes = remoteThumbs[item.packageName],
                        onRequestRemoteThumb = onRequestRemoteThumb,
                        onToggle = { onToggle(index) },
                        onAction = { onAction(index, it) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(modifier = Modifier.weight(1f), onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onApply,
                    enabled = !busy && current.selectedCount > 0,
                ) {
                    Text(stringResource(R.string.sync_apply))
                }
            }
        }
    }
}

@Composable
private fun SyncApplyProgressBar(fraction: Float) {
    val trackColor = MiuixTheme.colorScheme.surfaceContainerHigh
    val indicatorColor = MiuixTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp))
                .background(indicatorColor),
        )
    }
}

@Composable
private fun SyncDiffRow(
    item: ProjectSyncDiffItem,
    localFile: File?,
    remoteBytes: ByteArray?,
    onRequestRemoteThumb: (String) -> Unit,
    onToggle: () -> Unit,
    onAction: (ProjectSyncAction) -> Unit,
) {
    val showLocal = item.kind == ProjectSyncKind.missingOnRemote ||
        item.kind == ProjectSyncKind.localOnly ||
        item.kind == ProjectSyncKind.bothChanged
    val showRemote = item.kind == ProjectSyncKind.missingOnLocal ||
        item.kind == ProjectSyncKind.remoteOnly ||
        item.kind == ProjectSyncKind.bothChanged
    if (showRemote && item.packageName.isNotBlank()) {
        LaunchedEffect(item.packageName) {
            onRequestRemoteThumb(item.packageName)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SyncCheckMark(checked = item.selected)
            if (item.packageName.isNotBlank() && (showLocal || showRemote)) {
                SyncDiffIcons(
                    showLocal = showLocal,
                    showRemote = showRemote,
                    bothDifferent = item.kind == ProjectSyncKind.bothChanged,
                    localFile = localFile,
                    remoteBytes = remoteBytes,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = item.appName.ifBlank { stringResource(R.string.sync_metadata_label) },
                    fontWeight = FontWeight.SemiBold,
                )
                if (item.packageName.isNotBlank()) {
                    Text(item.packageName, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                Text(
                    item.detail,
                    color = if (item.isDeletionChoice) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
        if (item.selected) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actionsFor(item).forEach { action ->
                    Button(onClick = { onAction(action) }) {
                        Text(
                            text = actionLabel(action, item),
                            color = if (item.action == action) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncDiffIcons(
    showLocal: Boolean,
    showRemote: Boolean,
    bothDifferent: Boolean,
    localFile: File?,
    remoteBytes: ByteArray?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLocal) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SyncThumb(file = localFile, bytes = null)
                if (bothDifferent) {
                    Text(
                        stringResource(R.string.sync_thumb_local),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
            }
        }
        if (showRemote) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SyncThumb(file = null, bytes = remoteBytes)
                if (bothDifferent) {
                    Text(
                        stringResource(R.string.sync_thumb_remote),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncThumb(file: File?, bytes: ByteArray?) {
    val bitmap = remember(file?.absolutePath, file?.lastModified(), bytes) {
        when {
            bytes != null && bytes.isNotEmpty() ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            file != null && file.isFile && file.extension.lowercase() != "svg" ->
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            else -> null
        }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

private fun actionsFor(item: ProjectSyncDiffItem): List<ProjectSyncAction> = when (item.kind) {
    ProjectSyncKind.bothChanged, ProjectSyncKind.metadataChanged -> listOf(
        ProjectSyncAction.pullToLocal,
        ProjectSyncAction.pushToRemote,
        ProjectSyncAction.skip,
    )
    ProjectSyncKind.missingOnRemote -> listOf(
        ProjectSyncAction.pushToRemote,
        ProjectSyncAction.deleteLocal,
        ProjectSyncAction.skip,
    )
    ProjectSyncKind.missingOnLocal -> listOf(
        ProjectSyncAction.pullToLocal,
        ProjectSyncAction.deleteRemote,
        ProjectSyncAction.skip,
    )
    else -> listOf(ProjectSyncAction.pullToLocal, ProjectSyncAction.pushToRemote, ProjectSyncAction.skip)
}

@Composable
private fun actionLabel(action: ProjectSyncAction, item: ProjectSyncDiffItem): String = when (action) {
    ProjectSyncAction.pullToLocal -> when (item.kind) {
        ProjectSyncKind.metadataChanged -> stringResource(R.string.sync_action_use_remote)
        ProjectSyncKind.missingOnLocal -> stringResource(R.string.sync_action_remote_added)
        else -> stringResource(R.string.sync_action_pull)
    }
    ProjectSyncAction.pushToRemote -> when (item.kind) {
        ProjectSyncKind.metadataChanged -> stringResource(R.string.sync_action_use_local)
        ProjectSyncKind.missingOnRemote -> stringResource(R.string.sync_action_local_added)
        else -> stringResource(R.string.sync_action_push)
    }
    ProjectSyncAction.deleteLocal -> when (item.kind) {
        ProjectSyncKind.missingOnRemote -> stringResource(R.string.sync_action_peer_deleted)
        else -> stringResource(R.string.sync_action_delete_local)
    }
    ProjectSyncAction.deleteRemote -> when (item.kind) {
        ProjectSyncKind.missingOnLocal -> stringResource(R.string.sync_action_local_deleted)
        else -> stringResource(R.string.sync_action_delete_remote)
    }
    ProjectSyncAction.skip -> stringResource(R.string.sync_action_skip)
}

@Composable
private fun SyncCheckMark(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (checked) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceContainerHighest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MiuixTheme.colorScheme.onPrimary,
            )
        }
    }
}
