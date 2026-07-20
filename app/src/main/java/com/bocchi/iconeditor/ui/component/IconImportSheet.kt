package com.bocchi.iconeditor.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.IconImportCandidate
import com.bocchi.iconeditor.model.IconImportPreview
import com.bocchi.iconeditor.ui.page.IconPreview
import com.bocchi.iconeditor.ui.page.Tag
import java.io.File
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val allSelected = preview?.items?.let { items ->
        items.isNotEmpty() && items.all { it.selected }
    } == true
    OverlayBottomSheet(
        show = preview != null,
        title = stringResource(R.string.icon_import_title),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                )
            }
        },
        endAction = {
            IconButton(onClick = if (allSelected) onSelectNone else onSelectAll) {
                Icon(
                    imageVector = MiuixIcons.SelectAll,
                    contentDescription = stringResource(
                        if (allSelected) {
                            R.string.icon_import_select_none
                        } else {
                            R.string.icon_import_select_all
                        },
                    ),
                    tint = if (allSelected) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                )
            }
        },
        onDismissRequest = onDismiss,
    ) {
        val current = preview
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .padding(bottom = navigationBarBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current != null) {
                Text(
                    text = stringResource(
                        R.string.icon_import_summary,
                        current.totalIncoming,
                        current.conflictCount,
                        current.newCount,
                        current.selectedCount,
                    ),
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onOverwrite,
                    enabled = (preview?.selectedCount ?: 0) > 0,
                ) {
                    Text(stringResource(R.string.icon_import_overwrite))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onAddOnly,
                    enabled = (preview?.selectedNewCount ?: 0) > 0,
                ) {
                    Text(stringResource(R.string.icon_import_add_only))
                }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        ),
        onClick = onToggle,
        showIndication = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconPreview(
                file = file,
                packageName = item.packageName,
            )
            Spacer(Modifier.width(12.dp))
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
                text = stringResource(
                    if (item.conflict) {
                        R.string.icon_import_tag_conflict
                    } else {
                        R.string.icon_import_tag_new
                    },
                ),
            )
            Spacer(Modifier.width(8.dp))
            Checkbox(
                state = if (item.selected) ToggleableState.On else ToggleableState.Off,
                onClick = null,
            )
        }
    }
}
