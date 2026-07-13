package com.bocchi.iconeditor.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.MaskLayerImportCandidate
import com.bocchi.iconeditor.model.MaskLayerImportPreview
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
fun MaskLayerImportConfirmDialog(
    preview: MaskLayerImportPreview?,
    layerFile: (MaskLayerImportCandidate) -> File?,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    val sourceLabel = when (preview?.sourceType) {
        SourceType.Apk -> stringResource(R.string.icon_import_source_apk)
        SourceType.Mtz -> stringResource(R.string.icon_import_source_mtz)
        SourceType.Module -> stringResource(R.string.icon_import_source_module)
        SourceType.Universal, null -> ""
    }
    OverlayDialog(
        show = preview != null,
        title = stringResource(R.string.mask_import_title),
        summary = preview?.let {
            stringResource(
                R.string.mask_import_summary,
                it.displayName,
                sourceLabel,
                it.foundCount,
                it.conflictCount,
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
                current.items.forEach { item ->
                    MaskLayerImportRow(
                        item = item,
                        file = layerFile(item),
                        onToggle = { if (item.found) onToggle(item.layerName) },
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onImport,
                enabled = (preview?.selectedCount ?: 0) > 0,
            ) {
                Text(stringResource(R.string.mask_import_apply))
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun MaskLayerImportRow(
    item: MaskLayerImportCandidate,
    file: File?,
    onToggle: () -> Unit,
) {
    val enabled = item.found
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaskImportCheckMark(checked = item.selected && item.found, enabled = enabled)
        Spacer(modifier = Modifier.width(10.dp))
        IconPreview(file = file, size = 44.dp, imageSize = 38.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.layerName,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = when (item.layerName) {
                    "iconback" -> stringResource(R.string.apk_mask_back_desc)
                    "iconmask" -> stringResource(R.string.apk_mask_mask_desc)
                    "iconupon" -> stringResource(R.string.apk_mask_upon_desc)
                    else -> item.layerName
                },
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Tag(
            text = when {
                !item.found -> stringResource(R.string.mask_import_tag_missing)
                item.conflict -> stringResource(R.string.icon_import_tag_conflict)
                else -> stringResource(R.string.icon_import_tag_new)
            },
        )
    }
}

@Composable
private fun MaskImportCheckMark(checked: Boolean, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    !enabled -> MiuixTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                    checked -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.surfaceContainerHighest
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked && enabled) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MiuixTheme.colorScheme.onPrimary,
            )
        }
    }
}
