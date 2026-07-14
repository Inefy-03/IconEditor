package com.bocchi.iconeditor.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ImportProgressOverlay(progress: ImportProgress?) {
    if (progress == null) return
    TaskProgressOverlay(
        title = importProgressLabel(progress),
        detail = null,
        logs = emptyList(),
        fraction = progress.fraction,
    )
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
    TaskProgressOverlay(
        title = exportProgressLabel(progress),
        detail = progress.detail.takeIf { it.isNotBlank() },
        logs = progress.logs,
        fraction = progress.fraction,
        finished = progress.finished,
        success = progress.success,
        onDismiss = onDismiss,
        installUri = installUri.takeIf { progress.finished && progress.success },
        onInstall = onInstall,
        onOpenDirectory = onOpenDirectory.takeIf { progress.finished && progress.success },
        allowCopyLogs = true,
    )
}

@Composable
private fun TaskProgressOverlay(
    title: String,
    detail: String?,
    logs: List<String>,
    fraction: Float,
    finished: Boolean = false,
    success: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    installUri: Uri? = null,
    onInstall: ((Uri) -> Unit)? = null,
    onOpenDirectory: (() -> Unit)? = null,
    allowCopyLogs: Boolean = false,
) {
    val context = LocalContext.current
    val logScrollState = rememberScrollState()
    // 瞬时滚到底，避免 animateScrollTo 与频繁布局叠加导致抖动
    LaunchedEffect(logs.size, logs.lastOrNull()) {
        if (logs.isNotEmpty()) {
            logScrollState.scrollTo(logScrollState.maxValue)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 固定详情区高度，避免 detail 变长时整窗上移
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (allowCopyLogs) 36.dp else if (!detail.isNullOrBlank()) 36.dp else 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ImportProgressBar(fraction = fraction)
            Text(
                text = "${(fraction * 100).toInt()}%",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (allowCopyLogs || logs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .verticalScroll(logScrollState)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "…",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f),
                        )
                    } else {
                        logs.takeLast(48).forEach { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
            if (allowCopyLogs) {
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
                    Text(stringResource(R.string.export_copy_logs))
                }
            }
            if (finished && onDismiss != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (success && installUri != null && onInstall != null) {
                        Text(
                            text = stringResource(R.string.export_apk_install_summary),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = onDismiss,
                            ) {
                                Text(stringResource(R.string.export_apk_install_no))
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { onInstall(installUri) },
                            ) {
                                Text(stringResource(R.string.export_apk_install_yes))
                            }
                        }
                        if (onOpenDirectory != null) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onOpenDirectory,
                            ) {
                                Text(stringResource(R.string.export_open_directory))
                            }
                        }
                    } else {
                        if (success && onOpenDirectory != null) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onOpenDirectory,
                            ) {
                                Text(stringResource(R.string.export_open_directory))
                            }
                        }
                        Button(onClick = onDismiss) {
                            Text(
                                text = if (success) {
                                    stringResource(R.string.export_close_success)
                                } else {
                                    stringResource(R.string.export_close)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
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
private fun exportProgressLabel(progress: ExportProgress): String = when {
    progress.finished && progress.success -> stringResource(R.string.export_phase_done)
    progress.finished && !progress.success -> stringResource(R.string.export_phase_failed)
    progress.phase == ExportPhase.Preparing -> stringResource(R.string.export_phase_preparing)
    progress.phase == ExportPhase.PackagingIcons -> {
        if (progress.total > 0) {
            stringResource(R.string.export_phase_packaging_icons, progress.current, progress.total)
        } else {
            stringResource(R.string.export_phase_preparing)
        }
    }
    progress.phase == ExportPhase.WritingArchive -> stringResource(R.string.export_phase_writing_archive)
    progress.phase == ExportPhase.Signing -> stringResource(R.string.export_phase_signing)
    progress.phase == ExportPhase.Finishing -> stringResource(R.string.export_phase_finishing)
    else -> stringResource(R.string.export_phase_preparing)
}

@Composable
private fun ImportProgressBar(fraction: Float) {
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
