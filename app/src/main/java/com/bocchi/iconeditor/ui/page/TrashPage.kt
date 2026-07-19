package com.bocchi.iconeditor.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.TrashEntry
import com.bocchi.iconeditor.ui.component.EmptyState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.text.DateFormat
import java.util.Date

@Composable
fun TrashPage(
    entries: List<TrashEntry>,
    contentPadding: PaddingValues = PaddingValues(top = 12.dp, bottom = 12.dp),
    onRestore: (String) -> Unit,
    onPurge: (String) -> Unit,
    onEmpty: () -> Unit,
) {
    var purgeTarget by remember { mutableStateOf<TrashEntry?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val emptyHeight = (
            maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding()
            ).coerceAtLeast(0.dp)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            if (entries.isNotEmpty()) {
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        onClick = { confirmEmpty = true },
                    ) {
                        Text(stringResource(R.string.action_empty_trash))
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        text = stringResource(R.string.trash_empty),
                        icon = MiuixIcons.Delete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(emptyHeight),
                    )
                }
            } else {
                items(entries, key = { it.project.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                entry.project.name,
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(
                                    R.string.trash_deleted_at,
                                    dateFormat.format(Date(entry.deletedAt)),
                                ),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.subtitle,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { onRestore(entry.project.id) },
                                ) {
                                    Text(stringResource(R.string.action_restore))
                                }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { purgeTarget = entry },
                                ) {
                                    Text(stringResource(R.string.action_purge))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = purgeTarget != null,
        title = stringResource(R.string.trash_delete_title),
        summary = purgeTarget?.let {
            stringResource(R.string.trash_delete_summary, it.project.name)
        },
        onDismissRequest = { purgeTarget = null },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = { purgeTarget = null }) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    purgeTarget?.let { onPurge(it.project.id) }
                    purgeTarget = null
                },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        }
    }

    OverlayDialog(
        show = confirmEmpty,
        title = stringResource(R.string.trash_empty_title),
        summary = stringResource(R.string.trash_empty_summary),
        onDismissRequest = { confirmEmpty = false },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = { confirmEmpty = false }) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    confirmEmpty = false
                    onEmpty()
                },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        }
    }
}
