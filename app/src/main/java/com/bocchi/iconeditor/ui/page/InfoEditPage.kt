package com.bocchi.iconeditor.ui.page

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.ApkInfo
import com.bocchi.iconeditor.model.InfoTab
import com.bocchi.iconeditor.model.ModuleInfo
import com.bocchi.iconeditor.model.MtzInfo
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.ui.component.withPageMargins
import java.io.File
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.anim.SinOutEasing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun InfoEditPage(
    metadata: ProjectMetadata,
    selectedTab: InfoTab,
    contentPadding: PaddingValues = PaddingValues(),
    onSaveMtz: (MtzInfo) -> Unit,
    onSaveModule: (ModuleInfo) -> Unit,
    onSaveApk: (ApkInfo) -> Unit,
    launcherIconFile: File? = null,
    iconBackFile: File? = null,
    iconMaskFile: File? = null,
    iconUponFile: File? = null,
    onPickLauncherIcon: () -> Unit = {},
    onClearLauncherIcon: () -> Unit = {},
    onPickIconBack: () -> Unit = {},
    onClearIconBack: () -> Unit = {},
    onPickIconMask: () -> Unit = {},
    onClearIconMask: () -> Unit = {},
    onPickIconUpon: () -> Unit = {},
    onClearIconUpon: () -> Unit = {},
    onImportMaskFromPack: () -> Unit = {},
) {
    val pagePadding = contentPadding.withPageMargins(horizontal = 16.dp)
    AnimatedContent(
        targetState = selectedTab,
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            slideInHorizontally(tween(350, easing = SinOutEasing)) { it * direction } togetherWith
                slideOutHorizontally(tween(350, easing = SinOutEasing)) { it * -direction }
        },
        modifier = Modifier.fillMaxSize(),
    ) { tab ->
        when (tab) {
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
                iconBackFile = iconBackFile,
                iconMaskFile = iconMaskFile,
                iconUponFile = iconUponFile,
                onPickLauncherIcon = onPickLauncherIcon,
                onClearLauncherIcon = onClearLauncherIcon,
                onPickIconBack = onPickIconBack,
                onClearIconBack = onClearIconBack,
                onPickIconMask = onPickIconMask,
                onClearIconMask = onClearIconMask,
                onPickIconUpon = onPickIconUpon,
                onClearIconUpon = onClearIconUpon,
                onImportMaskFromPack = onImportMaskFromPack,
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
    LazyColumn(
        modifier = modifier
            .imePadding()
            .overScrollVertical(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = contentPadding,
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
    LazyColumn(
        modifier = modifier
            .imePadding()
            .overScrollVertical(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = contentPadding,
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
    iconBackFile: File? = null,
    iconMaskFile: File? = null,
    iconUponFile: File? = null,
    onPickLauncherIcon: () -> Unit = {},
    onClearLauncherIcon: () -> Unit = {},
    onPickIconBack: () -> Unit = {},
    onClearIconBack: () -> Unit = {},
    onPickIconMask: () -> Unit = {},
    onClearIconMask: () -> Unit = {},
    onPickIconUpon: () -> Unit = {},
    onClearIconUpon: () -> Unit = {},
    onImportMaskFromPack: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        modifier = modifier
            .imePadding()
            .overScrollVertical(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = contentPadding,
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
        item {
            MaskLayersEditor(
                iconBackFile = iconBackFile,
                iconMaskFile = iconMaskFile,
                iconUponFile = iconUponFile,
                onPickIconBack = onPickIconBack,
                onClearIconBack = onClearIconBack,
                onPickIconMask = onPickIconMask,
                onClearIconMask = onClearIconMask,
                onPickIconUpon = onPickIconUpon,
                onClearIconUpon = onClearIconUpon,
                onImportFromPack = onImportMaskFromPack,
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
private fun MaskLayersEditor(
    iconBackFile: File?,
    iconMaskFile: File?,
    iconUponFile: File?,
    onPickIconBack: () -> Unit,
    onClearIconBack: () -> Unit,
    onPickIconMask: () -> Unit,
    onClearIconMask: () -> Unit,
    onPickIconUpon: () -> Unit,
    onClearIconUpon: () -> Unit,
    onImportFromPack: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.apk_mask_layers),
                style = MiuixTheme.textStyles.subtitle,
            )
            Text(
                text = stringResource(R.string.mask_import_from_pack),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onImportFromPack),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaskLayerCard(
                modifier = Modifier.weight(1f),
                name = "iconback",
                description = stringResource(R.string.apk_mask_back_desc),
                footer = stringResource(R.string.apk_mask_back_footer),
                file = iconBackFile,
                onPick = onPickIconBack,
                onClear = onClearIconBack,
            )
            MaskLayerCard(
                modifier = Modifier.weight(1f),
                name = "iconmask",
                description = stringResource(R.string.apk_mask_mask_desc),
                footer = stringResource(R.string.apk_mask_mask_footer),
                file = iconMaskFile,
                onPick = onPickIconMask,
                onClear = onClearIconMask,
            )
            MaskLayerCard(
                modifier = Modifier.weight(1f),
                name = "iconupon",
                description = stringResource(R.string.apk_mask_upon_desc),
                footer = stringResource(R.string.apk_mask_upon_footer),
                file = iconUponFile,
                onPick = onPickIconUpon,
                onClear = onClearIconUpon,
            )
        }
    }
}

@Composable
private fun MaskLayerCard(
    name: String,
    description: String,
    footer: String,
    file: File?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surface)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.clickable(onClick = onPick)) {
            IconPreview(file = file, size = 52.dp, imageSize = 44.dp)
        }
        Text(text = name, style = MiuixTheme.textStyles.footnote1)
        Text(
            text = description,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stringResource(R.string.apk_mask_pick),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onPick),
        )
        Text(
            text = footer,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (file != null) {
            Text(
                text = stringResource(R.string.apk_asset_clear),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onClear),
            )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = title, style = MiuixTheme.textStyles.subtitle)
        Text(
            text = hint,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconPreview(file = file, size = 64.dp, imageSize = 56.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (file == null) {
                    Text(
                        text = stringResource(R.string.apk_asset_empty),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = onPick) {
                        Text(stringResource(R.string.apk_asset_pick))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = file != null,
                        onClick = onClear,
                    ) {
                        Text(stringResource(R.string.apk_asset_clear))
                    }
                }
            }
        }
    }
}

@Composable
fun LabeledField(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(300)
            bringIntoViewRequester.bringIntoView()
        }
    }
    TextField(
        value = value,
        onValueChange = onChange,
        label = label,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        maxLines = if (singleLine) 1 else 8,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
    )
}
