package com.bocchi.iconeditor.ui.page

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.anim.SinOutEasing
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun InfoEditPage(
    metadata: ProjectMetadata,
    selectedTab: InfoTab,
    contentPadding: PaddingValues = PaddingValues(),
    onSaveMtz: (MtzInfo) -> Unit,
    onSaveModule: (ModuleInfo) -> Unit,
    onSaveApk: (ApkInfo) -> Unit,
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
