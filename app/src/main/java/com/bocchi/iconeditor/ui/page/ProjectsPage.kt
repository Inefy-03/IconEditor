package com.bocchi.iconeditor.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.ProjectSortField
import com.bocchi.iconeditor.model.ProjectSummary
import com.bocchi.iconeditor.model.SourceType
import com.bocchi.iconeditor.ui.component.EmptyState
import com.bocchi.iconeditor.ui.component.label
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ProjectsPage(
    projects: List<ProjectSummary>,
    metadata: Map<String, ProjectMetadata>,
    sortField: ProjectSortField = ProjectSortField.CreatedAt,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    scrollToTopRequest: Int = 0,
    onEditInfo: (ProjectSummary) -> Unit,
    onEditIcons: (ProjectSummary) -> Unit,
    onRename: (ProjectSummary) -> Unit,
    onDelete: (ProjectSummary) -> Unit,
    onExport: (ProjectSummary) -> Unit,
) {
    val gridState = key(sortField) { rememberLazyGridState() }
    val sortedProjects = remember(projects, metadata, sortField) {
        sortProjects(projects, metadata, sortField)
    }
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0 && projects.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }
    if (projects.isEmpty()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val emptyHeight = (
                maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding()
                ).coerceAtLeast(0.dp)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                item {
                    EmptyState(
                        text = stringResource(R.string.empty_projects),
                        icon = MiuixIcons.FolderFill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(emptyHeight),
                    )
                }
            }
        }
    } else {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical(),
            state = gridState,
            columns = GridCells.Adaptive(350.dp),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            gridItems(sortedProjects, key = { it.id }) { project ->
                ProjectCard(
                    project,
                    metadata[project.id] ?: ProjectMetadata(),
                    onEditInfo,
                    onEditIcons,
                    onRename,
                    onDelete,
                    onExport,
                )
            }
        }
    }
}

internal fun sortProjects(
    projects: List<ProjectSummary>,
    metadata: Map<String, ProjectMetadata>,
    sortField: ProjectSortField,
): List<ProjectSummary> = when (sortField) {
    ProjectSortField.CreatedAt -> projects.sortedWith(
        compareByDescending<ProjectSummary> { it.createdAt }.thenByDescending { it.updatedAt },
    )
    ProjectSortField.UpdatedAt -> projects.sortedWith(
        compareByDescending<ProjectSummary> { it.updatedAt }.thenByDescending { it.createdAt },
    )
    ProjectSortField.Name -> projects.sortedWith(
        compareBy<ProjectSummary> { projectDisplayTitle(it, metadata[it.id] ?: ProjectMetadata()).lowercase() }
            .thenBy { it.createdAt },
    )
}

@Composable
fun ProjectCard(
    project: ProjectSummary,
    metadata: ProjectMetadata,
    onEditInfo: (ProjectSummary) -> Unit,
    onEditIcons: (ProjectSummary) -> Unit,
    onRename: (ProjectSummary) -> Unit,
    onDelete: (ProjectSummary) -> Unit,
    onExport: (ProjectSummary) -> Unit,
) {
    val displayTitle = projectDisplayTitle(project, metadata)
    val displayVersion = projectDisplayVersion(project, metadata)
    val displayAuthor = projectDisplayAuthor(project, metadata)
    val showMetadata = projectHasVersionOrAuthor(metadata)
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
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
                ProjectBadge(project.sourceType.label())
            }
            if (showMetadata) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (displayVersion.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.project_version_format, displayVersion),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (displayAuthor.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.project_author_format, displayAuthor),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectActionButton(
                imageVector = MiuixIcons.Rename,
                contentDescription = stringResource(R.string.action_rename),
                onClick = { onRename(project) },
            )
            ProjectActionButton(
                imageVector = MiuixIcons.Edit,
                contentDescription = stringResource(R.string.action_edit_info),
                onClick = { onEditInfo(project) },
            )
            ProjectActionButton(
                imageVector = MiuixIcons.GridView,
                contentDescription = stringResource(R.string.action_edit_icons),
                onClick = { onEditIcons(project) },
            )
            Spacer(Modifier.weight(1f))
            ProjectActionButton(
                imageVector = MiuixIcons.Download,
                contentDescription = stringResource(R.string.action_export),
                onClick = { onExport(project) },
            )
            ProjectActionButton(
                imageVector = MiuixIcons.Delete,
                contentDescription = stringResource(R.string.action_delete),
                danger = true,
                onClick = { onDelete(project) },
            )
        }
    }
}

@Composable
fun ProjectBadge(
    text: String,
    textColor: Color = MiuixTheme.colorScheme.primary,
    containerColor: Color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 18.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

fun projectDisplayTitle(project: ProjectSummary, metadata: ProjectMetadata): String {
    return when (project.sourceType) {
        SourceType.Mtz -> metadata.mtz.title
        SourceType.Module -> metadata.module.name
        SourceType.Apk -> metadata.apk.label
        SourceType.Universal -> metadata.mtz.title.ifBlank { metadata.module.name.ifBlank { metadata.apk.label } }
    }.ifBlank { project.name }
}

fun projectDisplayVersion(project: ProjectSummary, metadata: ProjectMetadata): String {
    return when (project.sourceType) {
        SourceType.Mtz -> metadata.mtz.version
        SourceType.Module -> metadata.module.version
        SourceType.Apk -> metadata.apk.versionName
        SourceType.Universal -> metadata.mtz.version.ifBlank { metadata.module.version.ifBlank { metadata.apk.versionName } }
    }
}

fun projectDisplayAuthor(project: ProjectSummary, metadata: ProjectMetadata): String {
    return when (project.sourceType) {
        SourceType.Mtz -> metadata.mtz.author
        SourceType.Module -> metadata.module.author
        SourceType.Apk -> metadata.apk.author
        SourceType.Universal -> metadata.mtz.author.ifBlank { metadata.module.author.ifBlank { metadata.apk.author } }
    }
}

fun projectHasVersionOrAuthor(metadata: ProjectMetadata): Boolean {
    return metadata.mtz.version.isNotBlank() ||
        metadata.mtz.author.isNotBlank() ||
        metadata.module.version.isNotBlank() ||
        metadata.module.author.isNotBlank()
}

@Composable
fun ProjectActionButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    label: String? = null,
) {
    val backgroundColor = if (danger) {
        MiuixTheme.colorScheme.errorContainer
    } else {
        MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    }
    val tint = if (danger) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.86f)

    IconButton(
        minHeight = 35.dp,
        minWidth = 35.dp,
        backgroundColor = backgroundColor,
        onClick = onClick,
    ) {
        if (label == null) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = imageVector,
                tint = tint,
                contentDescription = contentDescription,
            )
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = imageVector,
                    tint = tint,
                    contentDescription = null,
                )
                Text(
                    text = label,
                    color = tint,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
