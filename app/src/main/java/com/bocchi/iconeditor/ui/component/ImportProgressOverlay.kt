package com.bocchi.iconeditor.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.ExportPhase
import com.bocchi.iconeditor.model.ExportProgress
import com.bocchi.iconeditor.model.ImportPhase
import com.bocchi.iconeditor.model.ImportProgress
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ImportProgressOverlay(progress: ImportProgress?) {
    if (progress == null) return
    OverlayDialog(
        show = true,
        title = importProgressLabel(progress),
        onDismissRequest = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProgressBar(fraction = progress.fraction)
            Text(
                text = "${(progress.fraction * 100).toInt()}%",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
fun ExportProgressOverlay(
    progress: ExportProgress?,
    onDismiss: () -> Unit,
    installUri: Uri? = null,
    onInstall: ((Uri) -> Unit)? = null,
    onOpenDirectory: (() -> Unit)? = null,
) {
    if (progress == null) return
    if (!progress.finished) {
        ActiveExportDialog(progress = progress)
        return
    }

    var showResult by remember { mutableStateOf(true) }
    var showLogs by remember { mutableStateOf(false) }
    var dismissalFinished by remember { mutableStateOf(false) }
    val latestOnDismiss by rememberUpdatedState(onDismiss)

    fun requestDismiss() {
        showLogs = false
        showResult = false
    }

    LaunchedEffect(showResult, dismissalFinished) {
        if (!showResult && dismissalFinished) {
            latestOnDismiss()
        }
    }

    val canInstall = progress.success && installUri != null && onInstall != null
    OverlayDialog(
        show = showResult,
        title = stringResource(
            if (progress.success) R.string.export_phase_done else R.string.export_phase_failed,
        ),
        summary = when {
            canInstall -> stringResource(R.string.export_apk_install_summary)
            !progress.success -> progress.detail.takeIf { it.isNotBlank() }
            else -> null
        },
        onDismissRequest = ::requestDismiss,
        onDismissFinished = { dismissalFinished = true },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = progress.logs.isNotEmpty(),
                onClick = { showLogs = true },
            ) {
                ButtonLabel(stringResource(R.string.export_view_logs))
            }
            if (progress.success && onOpenDirectory != null) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenDirectory,
                ) {
                    ButtonLabel(stringResource(R.string.export_open_directory))
                }
            }
            if (canInstall) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = ::requestDismiss,
                    ) {
                        ButtonLabel(stringResource(R.string.export_apk_install_no))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onInstall(requireNotNull(installUri)) },
                    ) {
                        ButtonLabel(stringResource(R.string.export_apk_install_yes))
                    }
                }
            }
            if (!canInstall) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = ::requestDismiss,
                ) {
                    ButtonLabel(
                        stringResource(
                            if (progress.success) {
                                R.string.export_close_success
                            } else {
                                R.string.export_close
                            },
                        ),
                    )
                }
            }
        }
    }
    ExportLogSheet(
        show = showLogs,
        logs = progress.logs,
        onDismiss = { showLogs = false },
    )
}

@Composable
private fun ActiveExportDialog(progress: ExportProgress) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.export_progress_title),
        onDismissRequest = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiveLogPanel(logs = progress.logs)
            Text(
                text = exportProgressLabel(progress),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ProgressBar(fraction = progress.fraction)
            Text(
                text = "${(progress.fraction * 100).toInt()}%",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun LiveLogPanel(logs: List<String>) {
    val scrollState = rememberScrollState()
    LaunchedEffect(logs.size, logs.lastOrNull()) {
        if (logs.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainerHigh, 8.dp)
            .verticalScroll(scrollState)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (logs.isEmpty()) {
            LogLine(
                text = "…",
                colorAlpha = 0.45f,
            )
        } else {
            logs.takeLast(48).forEach { line ->
                LogLine(text = line)
            }
        }
    }
}

@Composable
private fun ExportLogSheet(
    show: Boolean,
    logs: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    LaunchedEffect(show, logs.size) {
        if (show && logs.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.export_logs_title),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                )
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = navigationBarBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 520.dp)
                    .squircleSurface(MiuixTheme.colorScheme.surfaceContainerHigh, 8.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                logs.forEach { line ->
                    LogLine(text = line)
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = logs.isNotEmpty(),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("export-log", logs.joinToString("\n")),
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_copy_logs_done),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                ButtonLabel(stringResource(R.string.export_copy_logs))
            }
        }
    }
}

@Composable
private fun LogLine(
    text: String,
    colorAlpha: Float = 1f,
) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = colorAlpha),
    )
}

@Composable
private fun importProgressLabel(progress: ImportProgress): String = when (progress.phase) {
    ImportPhase.Copying -> stringResource(R.string.import_phase_copying)
    ImportPhase.Extracting -> stringResource(R.string.import_phase_extracting)
    ImportPhase.ParsingIcons -> {
        if (progress.total > 0) {
            stringResource(R.string.import_phase_parsing_icons, progress.current, progress.total)
        } else {
            stringResource(R.string.import_phase_preparing_icons)
        }
    }
    ImportPhase.Finishing -> stringResource(R.string.import_phase_finishing)
}

@Composable
private fun exportProgressLabel(progress: ExportProgress): String = when (progress.phase) {
    ExportPhase.Preparing -> stringResource(R.string.export_phase_preparing)
    ExportPhase.PackagingIcons -> {
        if (progress.total > 0) {
            stringResource(R.string.export_phase_packaging_icons, progress.current, progress.total)
        } else {
            stringResource(R.string.export_phase_preparing)
        }
    }
    ExportPhase.WritingArchive -> stringResource(R.string.export_phase_writing_archive)
    ExportPhase.Signing -> stringResource(R.string.export_phase_signing)
    ExportPhase.Finishing -> stringResource(R.string.export_phase_finishing)
}

@Composable
private fun ProgressBar(fraction: Float) {
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
