package com.bocchi.iconeditor.ui.component

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.ExportFormat
import com.bocchi.iconeditor.model.ProjectSummary
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.model.SourceType
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EmptyState(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
fun ConfirmDeleteDialog(project: ProjectSummary?, metadata: ProjectMetadata?, onDismiss: () -> Unit, onConfirm: (ProjectSummary) -> Unit) {
    val displayName = if (project != null && metadata != null) {
        when (project.sourceType) {
            SourceType.Mtz -> metadata.mtz.title
            SourceType.Module -> metadata.module.name
            SourceType.Apk -> metadata.apk.label
            SourceType.Universal -> metadata.mtz.title.ifBlank { metadata.module.name.ifBlank { metadata.apk.label } }
        }.ifBlank { project.name }
    } else {
        project?.name.orEmpty()
    }
    OverlayDialog(
        show = project != null,
        title = stringResource(R.string.delete_project_title),
        summary = stringResource(R.string.delete_project_summary, displayName),
        onDismissRequest = onDismiss,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            Button(modifier = Modifier.weight(1f), onClick = { project?.let(onConfirm) }) { Text(stringResource(R.string.action_confirm)) }
        }
    }
}

@Composable
fun ExportDialog(
    project: ProjectSummary?,
    validate: (String, ExportFormat) -> List<String>,
    onDismiss: () -> Unit,
    onExport: (ProjectSummary, ExportFormat) -> Unit,
    onValidationFailed: (ProjectSummary, ExportFormat) -> Unit,
) {
    // Local visibility so choosing a format dismisses immediately and cannot be cancelled
    // mid-animation if parent state briefly flickers.
    var visible by remember { mutableStateOf(false) }
    var activeProject by remember { mutableStateOf<ProjectSummary?>(null) }
    LaunchedEffect(project) {
        if (project != null) {
            activeProject = project
            visible = true
        } else {
            visible = false
        }
    }
    OverlayDialog(
        show = visible,
        title = stringResource(R.string.export_format_title),
        onDismissRequest = {
            visible = false
            onDismiss()
        },
    ) {
        val p = activeProject
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                if (p == null) return@Button
                visible = false
                onDismiss()
                val errors = validate(p.id, ExportFormat.Mtz)
                if (errors.isEmpty()) onExport(p, ExportFormat.Mtz)
                else onValidationFailed(p, ExportFormat.Mtz)
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_mtz)) }
            Button(onClick = {
                if (p == null) return@Button
                visible = false
                onDismiss()
                val errors = validate(p.id, ExportFormat.ModuleZip)
                if (errors.isEmpty()) onExport(p, ExportFormat.ModuleZip)
                else onValidationFailed(p, ExportFormat.ModuleZip)
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_module_zip)) }
            Button(onClick = {
                if (p == null) return@Button
                visible = false
                onDismiss()
                val errors = validate(p.id, ExportFormat.Apk)
                if (errors.isEmpty()) onExport(p, ExportFormat.Apk)
                else onValidationFailed(p, ExportFormat.Apk)
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_apk)) }
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                visible = false
                onDismiss()
            }) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
fun ExportValidationDialog(
    format: ExportFormat?,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
) {
    OverlayDialog(
        show = format != null,
        title = stringResource(R.string.export_failed_title),
        summary = when (format) {
            ExportFormat.Mtz -> stringResource(R.string.export_mtz_incomplete)
            ExportFormat.ModuleZip -> stringResource(R.string.export_module_incomplete)
            ExportFormat.Apk -> stringResource(R.string.export_apk_incomplete)
            null -> null
        },
        onDismissRequest = onDismiss,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = onComplete) {
                Text(stringResource(R.string.action_complete_info))
            }
            Button(modifier = Modifier.weight(1f), onClick = onDismiss) {
                Text(stringResource(R.string.action_confirm))
            }
        }
    }
}

@Composable
fun MessageDialog(title: String?, message: String?, onDismiss: () -> Unit) {
    OverlayDialog(
        show = message != null,
        title = title ?: stringResource(R.string.dialog_notice),
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onDismiss,
        ) {
            Text(stringResource(R.string.action_confirm))
        }
    }
}

@Composable
fun SourceType.label(): String = when (this) {
    SourceType.Universal -> stringResource(R.string.source_universal)
    SourceType.Mtz -> stringResource(R.string.source_theme)
    SourceType.Module -> stringResource(R.string.source_module)
    SourceType.Apk -> stringResource(R.string.source_apk)
}

fun Context.displayName(uri: Uri): String {
    var cursor: Cursor? = null
    return runCatching {
        cursor = contentResolver.query(uri, null, null, null, null)
        val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
        val current = cursor
        if (current != null && current.moveToFirst() && nameIndex >= 0) current.getString(nameIndex).orEmpty() else uri.lastPathSegment.orEmpty()
    }.getOrDefault(uri.lastPathSegment.orEmpty()).ifBlank { "imported.zip" }
        .also { cursor?.close() }
}
