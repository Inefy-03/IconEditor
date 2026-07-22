package com.bocchi.iconeditor.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.model.TrashEntry
import com.bocchi.iconeditor.ui.component.ButtonLabel
import com.bocchi.iconeditor.ui.component.EmptyState
import com.bocchi.iconeditor.ui.component.label
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.DateFormat
import java.util.Date

@Composable
fun TrashPage(
    entries: List<TrashEntry>,
    metadata: Map<String, ProjectMetadata>,
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

    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (entries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.surface)
                        .padding(
                            start = 12.dp,
                            top = 12.dp,
                            end = 12.dp,
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TrashActionHeight),
                        onClick = { confirmEmpty = true },
                    ) {
                        ButtonLabel(stringResource(R.string.action_empty_trash))
                    }
                }
            }
        },
    ) { scaffoldPadding ->
        val listContentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = if (entries.isEmpty()) {
                contentPadding.calculateBottomPadding()
            } else {
                scaffoldPadding.calculateBottomPadding()
            },
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val emptyHeight = (
                maxHeight - listContentPadding.calculateTopPadding() -
                    listContentPadding.calculateBottomPadding()
                ).coerceAtLeast(0.dp)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = listContentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
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
                        val displayTitle = projectDisplayTitle(
                            entry.project,
                            metadata[entry.project.id] ?: ProjectMetadata(),
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            insideMargin = PaddingValues(0.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    top = 16.dp,
                                    end = 16.dp,
                                    bottom = 12.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = displayTitle,
                                        modifier = Modifier.weight(1f),
                                        style = MiuixTheme.textStyles.title4,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    ProjectBadge(entry.project.sourceType.label())
                                }
                                Text(
                                    stringResource(
                                        R.string.trash_deleted_at,
                                        dateFormat.format(Date(entry.deletedAt)),
                                    ),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.subtitle,
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 1.dp,
                                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.45f),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ProjectActionButton(
                                    imageVector = MiuixIcons.Reset,
                                    contentDescription = stringResource(R.string.action_restore),
                                    label = stringResource(R.string.action_restore),
                                    onClick = { onRestore(entry.project.id) },
                                )
                                Spacer(Modifier.weight(1f))
                                ProjectActionButton(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = stringResource(R.string.action_purge),
                                    danger = true,
                                    label = stringResource(R.string.action_purge),
                                    onClick = { purgeTarget = entry },
                                )
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
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(TrashActionHeight),
                onClick = { purgeTarget = null },
            ) {
                ButtonLabel(stringResource(R.string.action_cancel))
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(TrashActionHeight),
                onClick = {
                    purgeTarget?.let { onPurge(it.project.id) }
                    purgeTarget = null
                },
            ) {
                ButtonLabel(stringResource(R.string.action_confirm))
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
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(TrashActionHeight),
                onClick = { confirmEmpty = false },
            ) {
                ButtonLabel(stringResource(R.string.action_cancel))
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(TrashActionHeight),
                onClick = {
                    confirmEmpty = false
                    onEmpty()
                },
            ) {
                ButtonLabel(stringResource(R.string.action_confirm))
            }
        }
    }
}

private val TrashActionHeight = 48.dp
