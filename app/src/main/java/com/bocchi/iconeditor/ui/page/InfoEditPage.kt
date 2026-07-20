package com.bocchi.iconeditor.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.ApkInfo
import com.bocchi.iconeditor.model.InfoTab
import com.bocchi.iconeditor.model.ModuleInfo
import com.bocchi.iconeditor.model.MtzInfo
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.ui.component.CompactAssetActionButton
import com.bocchi.iconeditor.ui.component.withPageMargins
import java.io.File
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun InfoEditPage(
    metadata: ProjectMetadata,
    selectedTab: InfoTab,
    onSelectedTab: (InfoTab) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    onSaveMtz: (MtzInfo) -> Unit,
    onSaveModule: (ModuleInfo) -> Unit,
    onSaveApk: (ApkInfo) -> Unit,
    launcherIconFile: File? = null,
    onPickLauncherIcon: () -> Unit = {},
    onClearLauncherIcon: () -> Unit = {},
) {
    val pagePadding = contentPadding.withPageMargins(horizontal = 12.dp)
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { InfoTab.entries.size }
    val latestOnSelectedTab by rememberUpdatedState(onSelectedTab)

    LaunchedEffect(selectedTab, pagerState) {
        val selectedPage = selectedTab.ordinal
        if (pagerState.targetPage != selectedPage) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            latestOnSelectedTab(InfoTab.entries[page])
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
        beyondViewportPageCount = 1,
    ) { page ->
        when (InfoTab.entries[page]) {
            InfoTab.Mtz -> MtzInfoForm(
                info = metadata.mtz,
                onChange = onSaveMtz,
                modifier = Modifier.fillMaxSize(),
                contentPadding = pagePadding,
            )
            InfoTab.Module -> ModuleInfoForm(
                info = metadata.module,
                onChange = onSaveModule,
                modifier = Modifier.fillMaxSize(),
                contentPadding = pagePadding,
            )
            InfoTab.Apk -> ApkInfoForm(
                info = metadata.apk,
                onChange = onSaveApk,
                launcherIconFile = launcherIconFile,
                onPickLauncherIcon = onPickLauncherIcon,
                onClearLauncherIcon = onClearLauncherIcon,
                modifier = Modifier.fillMaxSize(),
                contentPadding = pagePadding,
            )
        }
    }
}

@Composable
fun MtzInfoForm(
    info: MtzInfo,
    onChange: (MtzInfo) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scrollContentPadding = contentPadding.withScrollableImeSafeArea()
    LazyColumn(
        modifier = modifier
            .scrollEndHaptic()
            .overScrollVertical(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = scrollContentPadding,
        overscrollEffect = null,
    ) {
        item { LabeledField("version", info.version) { onChange(info.copy(version = it)) } }
        item { LabeledField("author", info.author) { onChange(info.copy(author = it)) } }
        item { LabeledField("designer", info.designer) { onChange(info.copy(designer = it)) } }
        item { LabeledField("title", info.title) { onChange(info.copy(title = it)) } }
        item { LabeledField("description", info.description, singleLine = false) { onChange(info.copy(description = it)) } }
    }
}

@Composable
fun ModuleInfoForm(
    info: ModuleInfo,
    onChange: (ModuleInfo) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scrollContentPadding = contentPadding.withScrollableImeSafeArea()
    LazyColumn(
        modifier = modifier
            .scrollEndHaptic()
            .overScrollVertical(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = scrollContentPadding,
        overscrollEffect = null,
    ) {
        item { LabeledField("id", info.id) { onChange(info.copy(id = it)) } }
        item { LabeledField("name", info.name) { onChange(info.copy(name = it)) } }
        item { LabeledField("author", info.author) { onChange(info.copy(author = it)) } }
        item { LabeledField("description", info.description) { onChange(info.copy(description = it)) } }
        item { LabeledField("version", info.version) { onChange(info.copy(version = it)) } }
        item { LabeledField("theme", info.theme) { onChange(info.copy(theme = it)) } }
        item { LabeledField("themeid", info.themeId) { onChange(info.copy(themeId = it)) } }
        item {
            LabeledField(
                label = stringResource(R.string.module_install_messages),
                value = info.installMessages.joinToString("\n"),
                singleLine = false,
            ) { text -> onChange(info.copy(installMessages = text.lines())) }
        }
    }
}

@Composable
fun ApkInfoForm(
    info: ApkInfo,
    onChange: (ApkInfo) -> Unit,
    launcherIconFile: File? = null,
    onPickLauncherIcon: () -> Unit = {},
    onClearLauncherIcon: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scrollContentPadding = contentPadding.withScrollableImeSafeArea()
    LazyColumn(
        modifier = modifier
            .scrollEndHaptic()
            .overScrollVertical(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = scrollContentPadding,
        overscrollEffect = null,
    ) {
        item {
            ApkAssetEditor(
                title = stringResource(R.string.apk_launcher_icon),
                hint = stringResource(R.string.apk_launcher_icon_hint),
                file = launcherIconFile,
                onPick = onPickLauncherIcon,
                onClear = onClearLauncherIcon,
            )
        }
        item { LabeledField("package", info.packageName) { onChange(info.copy(packageName = it)) } }
        item { LabeledField("label", info.label) { onChange(info.copy(label = it)) } }
        item { LabeledField("author", info.author) { onChange(info.copy(author = it)) } }
        item { LabeledField("versionName", info.versionName) { onChange(info.copy(versionName = it)) } }
        item {
            LabeledField("versionCode", info.versionCode.toString()) { text ->
                onChange(info.copy(versionCode = text.toIntOrNull() ?: info.versionCode))
            }
        }
    }
}

@Composable
private fun ApkAssetEditor(
    title: String,
    hint: String,
    file: File?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconPreview(
                file = file,
                size = 76.dp,
                imageSize = 68.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = hint,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            CompactAssetActionButton(
                text = stringResource(
                    if (file == null) R.string.apk_asset_pick else R.string.apk_asset_clear,
                ),
                onClick = if (file == null) onPick else onClear,
            )
        }
    }
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    var isFocused by remember { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(isFocused, imeBottomPadding, fieldSize) {
        if (isFocused && fieldSize != IntSize.Zero) {
            delay(32)
            val clearancePx = with(density) { (imeBottomPadding + 16.dp).toPx() }
            bringIntoViewRequester.bringIntoView(
                Rect(
                    left = 0f,
                    top = 0f,
                    right = fieldSize.width.toFloat(),
                    bottom = fieldSize.height.toFloat() + clearancePx,
                ),
            )
        }
    }
    TextField(
        value = value,
        onValueChange = onChange,
        label = label,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        maxLines = if (singleLine) 1 else 8,
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { fieldSize = it }
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
    )
}

@Composable
internal fun PaddingValues.withScrollableImeSafeArea(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = maxOf(calculateBottomPadding(), imeBottomPadding + 16.dp),
    )
}
