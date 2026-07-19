package com.bocchi.iconeditor.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.IconImportCandidate
import com.bocchi.iconeditor.model.IconImportPreview
import com.bocchi.iconeditor.model.SourceType
import com.bocchi.iconeditor.ui.page.IconPreview
import com.bocchi.iconeditor.ui.page.Tag
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

@Composable
fun IconImportConfirmDialog(
    preview: IconImportPreview?,
    iconFile: (IconImportCandidate) -> File?,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onDismiss: () -> Unit,
    onOverwrite: () -> Unit,
    onAddOnly: () -> Unit,
) {
    val sourceLabel = when (preview?.sourceType) {
        SourceType.Apk -> stringResource(R.string.icon_import_source_apk)
        SourceType.Mtz -> stringResource(R.string.icon_import_source_mtz)
        SourceType.Module -> stringResource(R.string.icon_import_source_module)
        SourceType.Universal, null -> ""
    }
    OverlayDialog(
        show = preview != null,
        title = stringResource(R.string.icon_import_title),
        summary = preview?.let {
            stringResource(
                R.string.icon_import_summary,
                it.displayName,
                sourceLabel,
                it.totalIncoming,
                it.conflictCount,
                it.newCount,
                it.selectedCount,
            )
        },
        onDismissRequest = onDismiss,
    ) {
        val current = preview
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (current != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(current.items, key = { it.packageName }) { item ->
                        IconImportCandidateRow(
                            item = item,
                            file = iconFile(item),
                            onToggle = { onToggle(item.packageName) },
                        )
                    }
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOverwrite,
                enabled = (preview?.selectedCount ?: 0) > 0,
            ) {
                Text(stringResource(R.string.icon_import_overwrite))
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddOnly,
                enabled = (preview?.selectedNewCount ?: 0) > 0,
            ) {
                Text(stringResource(R.string.icon_import_add_only))
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun IconImportCandidateRow(
    item: IconImportCandidate,
    file: File?,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ImportCheckMark(checked = item.selected)
        Spacer(Modifier.width(10.dp))
        IconPreview(
            file = file,
            packageName = item.packageName,
            size = 44.dp,
            imageSize = 38.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.appName,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.packageName,
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Tag(
            text = if (item.conflict) {
                stringResource(R.string.icon_import_tag_conflict)
            } else {
                stringResource(R.string.icon_import_tag_new)
            },
        )
    }
}

@Composable
private fun ImportCheckMark(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (checked) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainerHighest
                },
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
