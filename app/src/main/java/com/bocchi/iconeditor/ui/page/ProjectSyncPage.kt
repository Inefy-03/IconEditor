package com.bocchi.iconeditor.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.data.sync.ProjectSyncAction
import com.bocchi.iconeditor.data.sync.ProjectSyncDiffItem
import com.bocchi.iconeditor.data.sync.ProjectSyncDiffPreview
import com.bocchi.iconeditor.data.sync.ProjectSyncKind
import com.bocchi.iconeditor.ui.component.ButtonLabel
import java.io.File
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleSurface

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
    onPeerHost: (String) -> Unit,
    onPeerPort: (String) -> Unit,
    onPeerToken: (String) -> Unit,
    onSavePeer: () -> Unit,
    onScanQr: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onProbe: () -> Unit,
) {
    val context = LocalContext.current
    val tokenClipboardLabel = stringResource(R.string.sync_peer_token)
    val copiedTokenMessage = stringResource(R.string.copied_format, serverToken)
    val canSavePeer = peerHost.isNotBlank() &&
        peerPort.toIntOrNull()?.let { it in 1..65535 } == true &&
        peerToken.isNotBlank()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical(),
        contentPadding = contentPadding.withScrollableImeSafeArea(),
        overscrollEffect = null,
    ) {
        item { SmallTitle(stringResource(R.string.sync_section_host)) }
        item {
            PreferenceCard(bottom = 6.dp) {
                SwitchPreference(
                    title = stringResource(R.string.sync_server_toggle),
                    summary = if (serverRunning) {
                        stringResource(
                            R.string.sync_server_running,
                            lanAddress ?: "0.0.0.0",
                            serverPort,
                        )
                    } else {
                        stringResource(R.string.sync_server_toggle_off)
                    },
                    checked = serverRunning,
                    onCheckedChange = { enabled ->
                        if (enabled) onStartServer() else onStopServer()
                    },
                )
                Text(
                    text = if (serverRunning) {
                        stringResource(R.string.sync_token_label, serverToken)
                    } else {
                        stringResource(R.string.sync_host_hint)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (serverRunning) {
                                Modifier.combinedClickable(
                                    enabled = serverToken.isNotBlank(),
                                    onClick = {},
                                    onLongClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                            as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(
                                                tokenClipboardLabel,
                                                serverToken,
                                            ),
                                        )
                                        Toast.makeText(
                                            context,
                                            copiedTokenMessage,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }

        item { SmallTitle(stringResource(R.string.sync_section_connect)) }
        item {
            PreferenceCard(bottom = 6.dp) {
                ArrowPreference(
                    title = stringResource(R.string.sync_scan_qr),
                    summary = stringResource(R.string.sync_scan_hint),
                    onClick = onScanQr,
                )
                LabeledField(
                    label = stringResource(R.string.sync_peer_host),
                    value = peerHost,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    onChange = onPeerHost,
                )
                LabeledField(
                    label = stringResource(R.string.sync_peer_port),
                    value = peerPort,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    onChange = onPeerPort,
                )
                LabeledField(
                    label = stringResource(R.string.sync_peer_token),
                    value = peerToken,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    onChange = onPeerToken,
                )
                ArrowPreference(
                    title = stringResource(R.string.sync_probe),
                    onClick = onProbe,
                )
                ArrowPreference(
                    title = stringResource(R.string.sync_save_peer),
                    onClick = onSavePeer,
                    enabled = canSavePeer,
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
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
        Column(
            modifier = Modifier.heightIn(max = 500.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                    ButtonLabel(stringResource(R.string.sync_select_local_added))
                }
                Button(modifier = Modifier.weight(1f), onClick = onSelectDeleteLocal) {
                    ButtonLabel(stringResource(R.string.sync_select_peer_deleted))
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
                    ButtonLabel(stringResource(R.string.sync_select_remote_added))
                }
                Button(modifier = Modifier.weight(1f), onClick = onSelectDeleteRemote) {
                    ButtonLabel(stringResource(R.string.sync_select_local_deleted))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(modifier = Modifier.weight(1f), onClick = onSelectAll) {
                    ButtonLabel(stringResource(R.string.icon_import_select_all))
                }
                Button(modifier = Modifier.weight(1f), onClick = onSelectNone) {
                    ButtonLabel(stringResource(R.string.icon_import_select_none))
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
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
                    ButtonLabel(stringResource(R.string.action_cancel))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onApply,
                    enabled = !busy && current.selectedCount > 0,
                ) {
                    ButtonLabel(stringResource(R.string.sync_apply))
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
                        ButtonLabel(
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
            .squircleSurface(MiuixTheme.colorScheme.secondaryContainer, 10.dp),
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
    val color = if (checked) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.surfaceContainerHighest
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .squircleBackground(color, 6.dp),
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
