package com.bocchi.iconeditor.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.data.ApkPackAssets
import com.bocchi.iconeditor.data.ThemePackAssets
import com.bocchi.iconeditor.model.SpecialAssetsTab
import com.bocchi.iconeditor.ui.component.CompactAssetActionButton
import java.io.File
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SpecialAssetsPage(
    selectedTab: SpecialAssetsTab,
    onSelectedTab: (SpecialAssetsTab) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    apkMaskFile: (ApkPackAssets.MaskLayer) -> File?,
    themeDrawableFile: (ThemePackAssets.DrawableAsset) -> File?,
    onPickApkMask: (ApkPackAssets.MaskLayer) -> Unit,
    onClearApkMask: (ApkPackAssets.MaskLayer) -> Unit,
    onImportMaskFromPack: () -> Unit,
    onPickThemeDrawable: (ThemePackAssets.DrawableAsset) -> Unit,
    onClearThemeDrawable: (ThemePackAssets.DrawableAsset) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) {
        SpecialAssetsTab.entries.size
    }
    val latestOnSelectedTab by rememberUpdatedState(onSelectedTab)

    LaunchedEffect(selectedTab, pagerState) {
        if (pagerState.targetPage != selectedTab.ordinal) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            latestOnSelectedTab(SpecialAssetsTab.entries[page])
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
        beyondViewportPageCount = 1,
    ) { page ->
        when (SpecialAssetsTab.entries[page]) {
            SpecialAssetsTab.Masks -> MaskAssetsList(
                contentPadding = contentPadding,
                apkMaskFile = apkMaskFile,
                themeDrawableFile = themeDrawableFile,
                onPickApkMask = onPickApkMask,
                onClearApkMask = onClearApkMask,
                onImportMaskFromPack = onImportMaskFromPack,
                onPickThemeDrawable = onPickThemeDrawable,
                onClearThemeDrawable = onClearThemeDrawable,
            )
            SpecialAssetsTab.SpecialIcons -> SpecialIconsList(
                contentPadding = contentPadding,
                themeDrawableFile = themeDrawableFile,
                onPickThemeDrawable = onPickThemeDrawable,
                onClearThemeDrawable = onClearThemeDrawable,
            )
        }
    }
}

@Composable
private fun MaskAssetsList(
    contentPadding: PaddingValues,
    apkMaskFile: (ApkPackAssets.MaskLayer) -> File?,
    themeDrawableFile: (ThemePackAssets.DrawableAsset) -> File?,
    onPickApkMask: (ApkPackAssets.MaskLayer) -> Unit,
    onClearApkMask: (ApkPackAssets.MaskLayer) -> Unit,
    onImportMaskFromPack: () -> Unit,
    onPickThemeDrawable: (ThemePackAssets.DrawableAsset) -> Unit,
    onClearThemeDrawable: (ThemePackAssets.DrawableAsset) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical(),
        contentPadding = contentPadding,
        overscrollEffect = null,
    ) {
        item { SmallTitle(stringResource(R.string.special_assets_apk_masks)) }
        item {
            SpecialAssetRow(
                title = stringResource(R.string.apk_mask_back_title),
                description = stringResource(R.string.apk_mask_back_desc),
                file = apkMaskFile(ApkPackAssets.MaskLayer.Back),
                onPick = { onPickApkMask(ApkPackAssets.MaskLayer.Back) },
                onClear = { onClearApkMask(ApkPackAssets.MaskLayer.Back) },
            )
        }
        item {
            SpecialAssetRow(
                title = stringResource(R.string.apk_mask_mask_title),
                description = stringResource(R.string.apk_mask_mask_desc),
                file = apkMaskFile(ApkPackAssets.MaskLayer.Mask),
                onPick = { onPickApkMask(ApkPackAssets.MaskLayer.Mask) },
                onClear = { onClearApkMask(ApkPackAssets.MaskLayer.Mask) },
            )
        }
        item {
            SpecialAssetRow(
                title = stringResource(R.string.apk_mask_upon_title),
                description = stringResource(R.string.apk_mask_upon_desc),
                file = apkMaskFile(ApkPackAssets.MaskLayer.Upon),
                onPick = { onPickApkMask(ApkPackAssets.MaskLayer.Upon) },
                onClear = { onClearApkMask(ApkPackAssets.MaskLayer.Upon) },
            )
        }
        item {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                onClick = onImportMaskFromPack,
            ) {
                Text(stringResource(R.string.mask_import_from_pack))
            }
        }

        item { SmallTitle(stringResource(R.string.special_assets_mtz_masks)) }
        item {
            SpecialAssetRow(
                title = ThemePackAssets.MtzMaskLayer.Pattern.resourceName,
                description = stringResource(R.string.mtz_mask_pattern_desc),
                file = themeDrawableFile(ThemePackAssets.MtzMaskLayer.Pattern),
                onPick = { onPickThemeDrawable(ThemePackAssets.MtzMaskLayer.Pattern) },
                onClear = { onClearThemeDrawable(ThemePackAssets.MtzMaskLayer.Pattern) },
            )
        }
        item {
            SpecialAssetRow(
                title = ThemePackAssets.MtzMaskLayer.Mask.resourceName,
                description = stringResource(R.string.mtz_mask_mask_desc),
                file = themeDrawableFile(ThemePackAssets.MtzMaskLayer.Mask),
                onPick = { onPickThemeDrawable(ThemePackAssets.MtzMaskLayer.Mask) },
                onClear = { onClearThemeDrawable(ThemePackAssets.MtzMaskLayer.Mask) },
            )
        }
        item {
            SpecialAssetRow(
                title = ThemePackAssets.MtzMaskLayer.Border.resourceName,
                description = stringResource(R.string.mtz_mask_border_desc),
                file = themeDrawableFile(ThemePackAssets.MtzMaskLayer.Border),
                onPick = { onPickThemeDrawable(ThemePackAssets.MtzMaskLayer.Border) },
                onClear = { onClearThemeDrawable(ThemePackAssets.MtzMaskLayer.Border) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SpecialIconsList(
    contentPadding: PaddingValues,
    themeDrawableFile: (ThemePackAssets.DrawableAsset) -> File?,
    onPickThemeDrawable: (ThemePackAssets.DrawableAsset) -> Unit,
    onClearThemeDrawable: (ThemePackAssets.DrawableAsset) -> Unit,
) {
    val rows = listOf(
        ThemePackAssets.SpecialIcon.WifiOff to stringResource(R.string.special_icon_wifi_off),
        ThemePackAssets.SpecialIcon.WifiOn to stringResource(R.string.special_icon_wifi_on),
        ThemePackAssets.SpecialIcon.DataOff to stringResource(R.string.special_icon_data_off),
        ThemePackAssets.SpecialIcon.DataOn to stringResource(R.string.special_icon_data_on),
        ThemePackAssets.SpecialIcon.BluetoothOff to stringResource(R.string.special_icon_bluetooth_off),
        ThemePackAssets.SpecialIcon.BluetoothOn to stringResource(R.string.special_icon_bluetooth_on),
        ThemePackAssets.SpecialIcon.HotspotOff to stringResource(R.string.special_icon_hotspot_off),
        ThemePackAssets.SpecialIcon.HotspotOn to stringResource(R.string.special_icon_hotspot_on),
        ThemePackAssets.SpecialIcon.TorchOff to stringResource(R.string.special_icon_torch_off),
        ThemePackAssets.SpecialIcon.TorchOn to stringResource(R.string.special_icon_torch_on),
        ThemePackAssets.SpecialIcon.FlightModeOff to stringResource(R.string.special_icon_flight_off),
        ThemePackAssets.SpecialIcon.FlightModeOn to stringResource(R.string.special_icon_flight_on),
        ThemePackAssets.SpecialIcon.Lock to stringResource(R.string.special_icon_lock),
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical(),
        contentPadding = contentPadding,
        overscrollEffect = null,
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        items(rows.size, key = { rows[it].first.resourceName }) { index ->
            val (asset, title) = rows[index]
            SpecialAssetRow(
                title = title,
                description = asset.resourceName,
                file = themeDrawableFile(asset),
                onPick = { onPickThemeDrawable(asset) },
                onClear = { onClearThemeDrawable(asset) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SpecialAssetRow(
    title: String,
    description: String,
    file: File?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        insideMargin = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPreview(
                file = file,
                size = 52.dp,
                imageSize = 46.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            CompactAssetActionButton(
                text = stringResource(
                    if (file == null) R.string.action_add_asset else R.string.action_delete,
                ),
                onClick = if (file == null) onPick else onClear,
            )
        }
    }
}
