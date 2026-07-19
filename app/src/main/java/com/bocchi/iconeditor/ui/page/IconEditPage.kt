package com.bocchi.iconeditor.ui.page

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bocchi.iconeditor.model.IconAsset
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.IconListItem
import com.bocchi.iconeditor.ui.component.navigationBarBottomPadding
import com.bocchi.iconeditor.ui.component.EmptyState
import com.bocchi.iconeditor.ui.component.withPageMargins
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.io.File

@Composable
fun IconEditPage(
    items: List<IconListItem>,
    contentPadding: PaddingValues = PaddingValues(),
    loading: Boolean = false,
    addIconRequest: Int = 0,
    iconFile: (IconAsset) -> File?,
    onConfirmEdits: (
        isNew: Boolean,
        originalPackageName: String,
        packageName: String,
        appName: String,
        aliasPackageNames: List<String>,
        selectedVariantKey: String?,
        selectedAdditionIndex: Int?,
        replacements: List<Pair<IconAsset, Uri>>,
        additions: List<Uri>,
    ) -> Boolean,
    onDeleteIcon: (IconAsset) -> Unit,
) {
    var visibleSheetPackageName by remember { mutableStateOf<String?>(null) }
    var retainedSheetPackageName by remember { mutableStateOf<String?>(null) }
    // Re-open after dismiss finishes — mid-animation reopen leaves OverlayBottomSheet stuck closed.
    var pendingOpenPackageName by remember { mutableStateOf<String?>(null) }
    var pendingOpenNew by remember { mutableStateOf(false) }
    var editingNew by remember { mutableStateOf(false) }
    var draftPackageName by remember { mutableStateOf("") }
    var draftAppName by remember { mutableStateOf("") }
    var draftAliasPackageNames by remember { mutableStateOf(listOf<String>()) }
    var originalPackageName by remember { mutableStateOf("") }
    var draftVariantKey by remember { mutableStateOf<String?>(null) }
    var draftSelectedAdditionIndex by remember { mutableStateOf<Int?>(null) }
    var draftReplacements by remember { mutableStateOf<List<Pair<IconAsset, Uri>>>(emptyList()) }
    var draftAdditions by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingImport by remember { mutableStateOf<PendingIconImport?>(null) }

    val blankItem = remember {
        IconListItem(
            packageName = "",
            appName = "",
            variants = emptyList(),
            selected = null,
            localApp = null,
            adapted = false,
            aliasPackageNames = emptyList(),
        )
    }
    val selectedItem = when {
        editingNew -> blankItem.copy(
            packageName = draftPackageName,
            appName = draftAppName,
            aliasPackageNames = draftAliasPackageNames,
        )
        retainedSheetPackageName != null ->
            items.firstOrNull { it.packageName == retainedSheetPackageName }
        else -> null
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val import = pendingImport
        if (uri != null && import != null) {
            when (import) {
                PendingIconImport.Add -> {
                    val additionIndex = draftAdditions.size
                    draftAdditions = draftAdditions + uri
                    if ((selectedItem?.variants.isNullOrEmpty() || editingNew) && draftSelectedAdditionIndex == null) {
                        draftVariantKey = null
                        draftSelectedAdditionIndex = additionIndex
                    }
                }
                is PendingIconImport.Replace -> {
                    draftReplacements = draftReplacements
                        .filterNot { it.first.archivePath == import.asset.archivePath } +
                        (import.asset to uri)
                }
                is PendingIconImport.ReplaceAddition -> {
                    if (import.index in draftAdditions.indices) {
                        draftAdditions = draftAdditions.toMutableList().apply {
                            this[import.index] = uri
                        }
                    }
                }
            }
        }
        pendingImport = null
    }

    fun resetDraftFields(item: IconListItem?, isNew: Boolean) {
        editingNew = isNew
        originalPackageName = if (isNew) "" else item?.packageName.orEmpty()
        draftPackageName = item?.packageName.orEmpty()
        draftAppName = item?.appName.orEmpty()
        draftAliasPackageNames = item?.aliasPackageNames.orEmpty()
        draftVariantKey = item?.selected?.variantKey
        draftSelectedAdditionIndex = null
        draftReplacements = emptyList()
        draftAdditions = emptyList()
    }

    fun openSheetNow(packageName: String) {
        val item = items.firstOrNull { it.packageName == packageName } ?: return
        resetDraftFields(item, isNew = false)
        retainedSheetPackageName = packageName
        visibleSheetPackageName = packageName
    }

    fun openNewSheetNow() {
        resetDraftFields(null, isNew = true)
        retainedSheetPackageName = "__new__"
        visibleSheetPackageName = "__new__"
    }

    fun openSheet(packageName: String) {
        when {
            visibleSheetPackageName != null -> openSheetNow(packageName)
            retainedSheetPackageName != null -> {
                pendingOpenNew = false
                pendingOpenPackageName = packageName
            }
            else -> openSheetNow(packageName)
        }
    }

    fun openNewSheet() {
        when {
            visibleSheetPackageName != null -> openNewSheetNow()
            retainedSheetPackageName != null -> {
                pendingOpenPackageName = null
                pendingOpenNew = true
            }
            else -> openNewSheetNow()
        }
    }

    fun requestCloseSheet() {
        pendingOpenPackageName = null
        pendingOpenNew = false
        visibleSheetPackageName = null
    }

    fun clearSheetRetention() {
        retainedSheetPackageName = null
        editingNew = false
        draftReplacements = emptyList()
        draftAdditions = emptyList()
        draftSelectedAdditionIndex = null
        draftAliasPackageNames = emptyList()
        pendingImport = null
    }

    LaunchedEffect(addIconRequest) {
        if (addIconRequest > 0) openNewSheet()
    }

    val listContentPadding = contentPadding.withPageMargins(vertical = 10.dp)
    if (loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            InfiniteProgressIndicator(
                color = Color.White,
                size = 32.dp,
                strokeWidth = 2.5.dp,
                orbitingDotSize = 3.dp,
            )
        }
    } else {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            val emptyHeight = (
                maxHeight - listContentPadding.calculateTopPadding() - listContentPadding.calculateBottomPadding()
                ).coerceAtLeast(0.dp)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = listContentPadding,
                overscrollEffect = null,
            ) {
                if (items.isEmpty()) {
                    item {
                        EmptyState(
                            text = stringResource(R.string.empty_icons),
                            icon = MiuixIcons.All,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(emptyHeight),
                        )
                    }
                } else {
                    items(items, key = { it.packageName }) { item ->
                        IconRow(
                            item = item,
                            iconFile = iconFile,
                            onClick = { openSheet(item.packageName) },
                        )
                    }
                }
            }
        }
    }
    OverlayBottomSheet(
        show = visibleSheetPackageName != null && selectedItem != null,
        title = stringResource(if (editingNew) R.string.action_add_icon else R.string.icon_edit_title),
        startAction = {
            IconButton(onClick = ::requestCloseSheet) {
                Icon(MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        endAction = {
            IconButton(onClick = {
                val item = selectedItem
                if (item != null) {
                    val ok = onConfirmEdits(
                        editingNew,
                        originalPackageName,
                        draftPackageName,
                        draftAppName,
                        draftAliasPackageNames,
                        draftVariantKey,
                        draftSelectedAdditionIndex,
                        draftReplacements,
                        draftAdditions,
                    )
                    if (ok) requestCloseSheet()
                }
            }) {
                Icon(MiuixIcons.Ok, contentDescription = stringResource(R.string.action_done))
            }
        },
        onDismissRequest = ::requestCloseSheet,
        onDismissFinished = {
            if (visibleSheetPackageName != null) return@OverlayBottomSheet
            clearSheetRetention()
            when {
                pendingOpenNew -> {
                    pendingOpenNew = false
                    pendingOpenPackageName = null
                    openNewSheetNow()
                }
                pendingOpenPackageName != null -> {
                    val next = pendingOpenPackageName
                    pendingOpenPackageName = null
                    if (next != null) openSheetNow(next)
                }
            }
        },
    ) {
        selectedItem?.let { item ->
            IconActionSheet(
                iconItem = item,
                isNew = editingNew,
                draftPackageName = draftPackageName,
                draftAppName = draftAppName,
                draftAliasPackageNames = draftAliasPackageNames,
                onPackageNameChange = { draftPackageName = it },
                onAppNameChange = { draftAppName = it },
                onAliasPackageNamesChange = { draftAliasPackageNames = it },
                iconFile = iconFile,
                draftVariantKey = draftVariantKey,
                draftSelectedAdditionIndex = draftSelectedAdditionIndex,
                draftReplacements = draftReplacements,
                draftAdditions = draftAdditions,
                onSelectExistingVariant = {
                    draftVariantKey = it
                    draftSelectedAdditionIndex = null
                },
                onSelectAddition = {
                    draftSelectedAdditionIndex = it
                    draftVariantKey = null
                },
                onStageReplace = { asset ->
                    pendingImport = PendingIconImport.Replace(asset)
                    imageLauncher.launch(arrayOf("image/png", "image/webp", "image/jpeg", "image/*"))
                },
                onDeleteIcon = onDeleteIcon,
                onDeleteAddition = { index ->
                    if (index in draftAdditions.indices) {
                        val remaining = draftAdditions.toMutableList().apply { removeAt(index) }
                        draftAdditions = remaining
                        val selectedIndex = draftSelectedAdditionIndex
                        when {
                            selectedIndex == index && remaining.isNotEmpty() -> {
                                draftSelectedAdditionIndex = index.coerceAtMost(remaining.lastIndex)
                                draftVariantKey = null
                            }
                            selectedIndex == index -> {
                                draftSelectedAdditionIndex = null
                                draftVariantKey = item.selected?.variantKey
                            }
                            selectedIndex != null && selectedIndex > index -> {
                                draftSelectedAdditionIndex = selectedIndex - 1
                            }
                        }
                    }
                },
                onStageReplaceAddition = { index ->
                    pendingImport = PendingIconImport.ReplaceAddition(index)
                    imageLauncher.launch(arrayOf("image/png", "image/webp", "image/jpeg", "image/*"))
                },
                onStageAdd = {
                    pendingImport = PendingIconImport.Add
                    imageLauncher.launch(arrayOf("image/png", "image/webp", "image/jpeg", "image/*"))
                },
            )
        }
    }
}

@Composable
fun IconRow(
    item: IconListItem,
    iconFile: (IconAsset) -> File?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        onClick = onClick,
        showIndication = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconPreview(
                file = item.selected?.let(iconFile),
                packageName = item.packageName,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.appName, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.packageName, style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val tags = iconStatusTags(item)
            if (tags.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tags.forEach { Tag(it) }
                }
            }
        }
    }
}

@Composable
fun IconActionSheet(
    iconItem: IconListItem,
    isNew: Boolean,
    draftPackageName: String,
    draftAppName: String,
    draftAliasPackageNames: List<String>,
    onPackageNameChange: (String) -> Unit,
    onAppNameChange: (String) -> Unit,
    onAliasPackageNamesChange: (List<String>) -> Unit,
    iconFile: (IconAsset) -> File?,
    draftVariantKey: String?,
    draftSelectedAdditionIndex: Int?,
    draftReplacements: List<Pair<IconAsset, Uri>>,
    draftAdditions: List<Uri>,
    onSelectExistingVariant: (String) -> Unit,
    onSelectAddition: (Int) -> Unit,
    onStageReplace: (IconAsset) -> Unit,
    onDeleteIcon: (IconAsset) -> Unit,
    onDeleteAddition: (Int) -> Unit,
    onStageReplaceAddition: (Int) -> Unit,
    onStageAdd: () -> Unit,
) {
    var pendingDeleteIndex by remember(iconItem.packageName) { mutableIntStateOf(-1) }
    var pendingDelete by remember(iconItem.packageName) { mutableStateOf<IconAsset?>(null) }
    var pendingDeleteAdditionIndex by remember(iconItem.packageName) { mutableIntStateOf(-1) }

    fun clearPendingDelete() {
        pendingDelete = null
        pendingDeleteIndex = -1
        pendingDeleteAdditionIndex = -1
    }

    val context = LocalContext.current
    val resources = LocalResources.current
    val previewVariant = iconItem.variants.find { it.variantKey == draftVariantKey }
    val replacementUris = remember(draftReplacements) {
        draftReplacements.associate { (asset, uri) -> asset.archivePath to uri }
    }
    val previewUri = previewVariant?.let { replacementUris[it.archivePath] }
        ?: draftSelectedAdditionIndex?.let(draftAdditions::getOrNull)
    val hasSelectedStyle = draftSelectedAdditionIndex != null || previewVariant != null
    val exportFileName = remember(iconItem.packageName, draftVariantKey, draftSelectedAdditionIndex) {
        val styleSuffix = when {
            draftSelectedAdditionIndex != null -> "_${iconItem.variants.size + draftSelectedAdditionIndex + 1}"
            previewVariant != null -> {
                val index = iconItem.variants.indexOfFirst { it.variantKey == previewVariant.variantKey }
                if (index >= 0) "_${index + 1}" else ""
            }
            else -> ""
        }
        "${iconItem.packageName}$styleSuffix.png"
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            val bytes = resolveExportPngBytes(
                context = context,
                previewUri = previewUri,
                previewFile = previewVariant?.let(iconFile),
                packageName = iconItem.packageName,
            ) ?: error("empty")
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            } ?: error("no stream")
        }.isSuccess
        val message = if (ok) {
            resources.getString(R.string.export_image_success, uri.lastPathSegment ?: exportFileName)
        } else {
            resources.getString(R.string.export_image_failed)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val hasPackageIcon = remember(iconItem.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(iconItem.packageName)
            true
        }.getOrDefault(false)
    }
    val canExportImage = previewUri != null ||
        previewVariant?.let(iconFile)?.isFile == true ||
        hasPackageIcon
    val hasStyles = iconItem.variants.isNotEmpty() || draftAdditions.isNotEmpty()
    val appNameLabel = stringResource(R.string.app_name_label)
    val packageNameLabel = stringResource(R.string.package_name_label)
    @Suppress("UNUSED_VARIABLE")
    val newIconMode = isNew

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 760.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconPreview(
                file = (previewVariant ?: iconItem.selected)?.let(iconFile),
                uri = previewUri,
                packageName = draftPackageName.ifBlank { iconItem.packageName }.ifBlank { null },
                size = 100.dp,
                imageSize = 90.dp,
            )
            TextField(
                value = draftAppName,
                onValueChange = onAppNameChange,
                label = appNameLabel,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = draftPackageName,
                onValueChange = onPackageNameChange,
                label = packageNameLabel,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.alias_package_names),
                style = MiuixTheme.textStyles.subtitle,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.alias_package_hint),
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth(),
            )
            if (draftAliasPackageNames.isEmpty()) {
                Text(
                    text = stringResource(R.string.alias_package_empty),
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                draftAliasPackageNames.forEachIndexed { index, alias ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField(
                            value = alias,
                            onValueChange = { value ->
                                onAliasPackageNamesChange(
                                    draftAliasPackageNames.toMutableList().also { it[index] = value },
                                )
                            },
                            label = packageNameLabel,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                onAliasPackageNamesChange(
                                    draftAliasPackageNames.toMutableList().also { it.removeAt(index) },
                                )
                            },
                        ) {
                            Icon(MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onAliasPackageNamesChange(draftAliasPackageNames + "") },
            ) {
                Text(stringResource(R.string.alias_package_add))
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = if (hasStyles) 12.dp else 0.dp,
            ),
        ) {
            if (iconItem.variants.isNotEmpty()) {
                itemsIndexed(iconItem.variants, key = { _, variant -> variant.variantKey }) { index, variant ->
                    val selected = draftSelectedAdditionIndex == null && draftVariantKey == variant.variantKey
                    val replacementUri = replacementUris[variant.archivePath]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                        ),
                        onClick = { onSelectExistingVariant(variant.variantKey) },
                        onLongPress = {
                            pendingDeleteIndex = index
                            pendingDelete = variant
                            pendingDeleteAdditionIndex = -1
                        },
                        showIndication = true,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconPreview(
                                file = iconFile(variant),
                                uri = replacementUri,
                                packageName = iconItem.packageName,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.style_number, index + 1), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (selected) {
                                Tag(stringResource(R.string.current_style))
                            }
                        }
                    }
                }
            }

            itemsIndexed(
                items = draftAdditions,
                key = { index, uri -> "draft-addition-$index-$uri" },
            ) { index, uri ->
                val selected = draftSelectedAdditionIndex == index
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                    ),
                    onClick = { onSelectAddition(index) },
                    onLongPress = {
                        pendingDelete = null
                        pendingDeleteIndex = iconItem.variants.size + index
                        pendingDeleteAdditionIndex = index
                    },
                    showIndication = true,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconPreview(
                            file = null,
                            uri = uri,
                            packageName = iconItem.packageName,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.style_number, iconItem.variants.size + index + 1),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) {
                            Tag(stringResource(R.string.current_style))
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(
                top = 6.dp,
                bottom = navigationBarBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onStageAdd,
                ) {
                    Text(stringResource(R.string.add_image))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = hasSelectedStyle,
                    onClick = {
                        when {
                            draftSelectedAdditionIndex != null ->
                                onStageReplaceAddition(draftSelectedAdditionIndex)
                            previewVariant != null -> onStageReplace(previewVariant)
                            iconItem.selected != null -> onStageReplace(iconItem.selected)
                            else -> Unit
                        }
                    },
                ) {
                    Text(stringResource(R.string.import_replace))
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canExportImage,
                onClick = { exportLauncher.launch(exportFileName) },
            ) {
                Text(stringResource(R.string.export_image))
            }
        }
    }

    OverlayDialog(
        show = pendingDelete != null || pendingDeleteAdditionIndex >= 0,
        title = stringResource(R.string.delete_style_title),
        summary = stringResource(R.string.delete_style_summary, pendingDeleteIndex + 1),
        onDismissRequest = ::clearPendingDelete,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = ::clearPendingDelete) { Text(stringResource(R.string.action_cancel)) }
            Button(modifier = Modifier.weight(1f), onClick = {
                pendingDelete?.let(onDeleteIcon)
                if (pendingDeleteAdditionIndex >= 0) onDeleteAddition(pendingDeleteAdditionIndex)
                clearPendingDelete()
            }) {
                Text(stringResource(R.string.action_confirm))
            }
        }
    }
}

private sealed interface PendingIconImport {
    data object Add : PendingIconImport
    data class Replace(val asset: IconAsset) : PendingIconImport
    data class ReplaceAddition(val index: Int) : PendingIconImport
}

@Composable
fun iconStatusTags(item: IconListItem): List<String> {
    return buildList {
        if (!item.adapted) add(stringResource(R.string.unadapted))
        if (item.variants.size > 1) {
            add(pluralStringResource(R.plurals.style_count, item.variants.size, item.variants.size))
        }
        if (item.aliasPackageNames.isNotEmpty()) {
            add(stringResource(R.string.alias_count_format, item.aliasPackageNames.size))
        }
    }
}

@Composable
fun IconPreview(
    file: File?,
    uri: Uri? = null,
    packageName: String? = null,
    size: Dp = 48.dp,
    imageSize: Dp = 42.dp,
) {
    val context = LocalContext.current
    val bitmap = remember(context, uri, file?.absolutePath, file?.lastModified(), packageName) {
        val draftBitmap = uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use(android.graphics.BitmapFactory::decodeStream)
            }.getOrNull()
        }
        val projectBitmap = draftBitmap ?: file?.takeIf {
            it.exists() && it.extension.lowercase() != "svg"
        }?.let {
            android.graphics.BitmapFactory.decodeFile(it.absolutePath)
        }
        projectBitmap ?: packageName?.let { packageIconBitmap(context, it) }
    }
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(imageSize),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ICON",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

fun packageIconBitmap(context: Context, packageName: String): android.graphics.Bitmap? {
    return runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val size = 96
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()
}

private fun resolveExportPngBytes(
    context: Context,
    previewUri: Uri?,
    previewFile: File?,
    packageName: String,
): ByteArray? {
    previewUri?.let { uri ->
        context.contentResolver.openInputStream(uri)?.use { input ->
            return input.readBytes()
        }
    }
    previewFile?.takeIf { it.isFile }?.let { file ->
        if (file.extension.lowercase() == "png") {
            return file.readBytes()
        }
        val decoded = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return@let
        return encodePng(decoded)
    }
    val packageBitmap = packageIconBitmap(context, packageName) ?: return null
    return encodePng(packageBitmap)
}

private fun encodePng(bitmap: android.graphics.Bitmap): ByteArray {
    val stream = java.io.ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

@Composable
fun Tag(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp),
            ),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            text = text,
            color = MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight(750),
            maxLines = 1,
            softWrap = false,
        )
    }
}
