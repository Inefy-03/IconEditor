package com.bocchi.iconeditor.ui.page

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.blend.ColorBlendToken
import com.bocchi.iconeditor.effect.BgEffectBackground
import com.bocchi.iconeditor.ui.component.miuixBarBlur
import com.bocchi.iconeditor.ui.component.rememberMiuixBlurBackdrop
import com.bocchi.iconeditor.ui.theme.isIconEditorDark
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
fun AboutPage(
    onBack: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val collapsedContentSpacingPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    var logoHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember(collapsedContentSpacingPx) {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                val collapseDistancePx = (logoHeightPx - collapsedContentSpacingPx).coerceAtLeast(1)
                if (index > 0) 1f else (offset.toFloat() / collapseDistancePx).coerceIn(0f, 1f)
            }
        }
    }
    val barBackdrop = rememberMiuixBlurBackdrop(enableBlur = true)
    val headerCollapsed = scrollProgress >= 0.999f
    val barBlurActive = barBackdrop != null && headerCollapsed
    val barColor = when {
        barBlurActive -> Color.Transparent
        headerCollapsed -> MiuixTheme.colorScheme.surface
        else -> Color.Transparent
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.miuixBarBlur(barBackdrop, enabled = barBlurActive)) {
                SmallTopAppBar(
                    title = stringResource(id = R.string.about_title),
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, contentDescription = stringResource(id = R.string.back))
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(barBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier),
        ) {
            AboutContentBody(
                padding = innerPadding,
                lazyListState = lazyListState,
                scrollBehavior = scrollBehavior,
                onLogoHeightChanged = { logoHeightPx = it }
            )
        }
    }
}

@Composable
private fun AboutContentBody(
    padding: PaddingValues,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: ScrollBehavior,
    onLogoHeightChanged: (Int) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val isDark = isIconEditorDark()
    val context = LocalContext.current
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val packageInfo = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "0.1.0"
    val versionCode = packageInfo?.longVersionCode ?: 1L

    // Background and effect configuration
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val blurEnable = shaderSupported
    val dynamicBackground = shaderSupported
    val effectBackground = shaderSupported
    val isOs3Effect = true

    val backdrop = rememberLayerBackdrop()

    val currentConfigValue = if (isDark) ColorBlendToken.Overlay_Extra_Thin_Dark else ColorBlendToken.Pured_Regular_Light

    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab)
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab)
            )
        }
    }

    // Header layout and deterministic scroll-driven animation
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var listViewportHeightPx by remember { mutableIntStateOf(0) }
    var actionCardHeightPx by remember { mutableIntStateOf(0) }
    var logoAreaBottomPx by remember(listViewportHeightPx) { mutableFloatStateOf(0f) }
    var iconBottomPx by remember(listViewportHeightPx) { mutableFloatStateOf(0f) }
    var projectNameBottomPx by remember(listViewportHeightPx) { mutableFloatStateOf(0f) }
    var versionCodeBottomPx by remember(listViewportHeightPx) { mutableFloatStateOf(0f) }

    val headerPositionsReady = logoAreaBottomPx > 0f &&
        iconBottomPx > 0f &&
        projectNameBottomPx > iconBottomPx &&
        versionCodeBottomPx > projectNameBottomPx
    val versionCodeFadeEnd = if (headerPositionsReady) {
        (logoAreaBottomPx - versionCodeBottomPx).coerceAtLeast(1f)
    } else {
        1f
    }
    val projectNameFadeEnd = versionCodeFadeEnd + if (headerPositionsReady) {
        (versionCodeBottomPx - projectNameBottomPx).coerceAtLeast(1f)
    } else {
        1f
    }
    val iconFadeEnd = projectNameFadeEnd + if (headerPositionsReady) {
        (projectNameBottomPx - iconBottomPx).coerceAtLeast(1f)
    } else {
        1f
    }
    val fadeLeadDistancePx = with(density) { 12.dp.toPx() }
    val versionCodeFadeStart = (versionCodeFadeEnd * 0.5f - fadeLeadDistancePx).coerceAtLeast(0f)
    val versionCodeFadeFinish = (versionCodeFadeEnd - fadeLeadDistancePx)
        .coerceAtLeast(versionCodeFadeStart + 1f)
    val projectNameFadeStart = versionCodeFadeFinish
    val projectNameFadeFinish = (projectNameFadeEnd - fadeLeadDistancePx)
        .coerceAtLeast(projectNameFadeStart + 1f)
    val iconFadeStart = projectNameFadeFinish
    val iconFadeFinish = (iconFadeEnd - fadeLeadDistancePx)
        .coerceAtLeast(iconFadeStart + 1f)
    val topBarBottomPx = with(density) { padding.calculateTopPadding().toPx() }
    val backgroundFadeFinish = (logoAreaBottomPx - topBarBottomPx - fadeLeadDistancePx)
        .coerceAtLeast(versionCodeFadeStart + 1f)
    val elementScrollOffsetPx = if (lazyListState.firstVisibleItemIndex > 0) {
        iconFadeFinish
    } else {
        lazyListState.firstVisibleItemScrollOffset.toFloat()
    }
    val backgroundScrollOffsetPx = if (lazyListState.firstVisibleItemIndex > 0) {
        backgroundFadeFinish
    } else {
        lazyListState.firstVisibleItemScrollOffset.toFloat()
    }
    val versionCodeProgress = if (headerPositionsReady) {
        collapseProgress(elementScrollOffsetPx, start = versionCodeFadeStart, end = versionCodeFadeFinish)
    } else {
        0f
    }
    val projectNameProgress = if (headerPositionsReady) {
        collapseProgress(elementScrollOffsetPx, start = projectNameFadeStart, end = projectNameFadeFinish)
    } else {
        0f
    }
    val iconProgress = if (headerPositionsReady) {
        collapseProgress(elementScrollOffsetPx, start = iconFadeStart, end = iconFadeFinish)
    } else {
        0f
    }
    val backgroundProgress = if (headerPositionsReady) {
        collapseProgress(
            backgroundScrollOffsetPx,
            start = versionCodeFadeStart,
            end = backgroundFadeFinish,
        )
    } else {
        0f
    }
    val scrollRunway = with(density) {
        val availableHeight = listViewportHeightPx.toDp() -
            padding.calculateTopPadding() -
            padding.calculateBottomPadding() -
            actionCardHeightPx.toDp() -
            12.dp
        availableHeight.coerceAtLeast(0.dp)
    }

    val displayCutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()

    val listContentPadding = PaddingValues(
        start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateStartPadding(layoutDirection),
        top = padding.calculateTopPadding(),
        end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateEndPadding(layoutDirection),
        bottom = padding.calculateBottomPadding() + scrollRunway
    )

    val logoPadding = PaddingValues(
        top = padding.calculateTopPadding() + 40.dp,
        start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateStartPadding(layoutDirection),
        end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateEndPadding(layoutDirection)
    )

    val bodyContent = @Composable {
        // Sticky animated header section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateStartPadding(layoutDirection),
                    end = logoPadding.calculateEndPadding(layoutDirection)
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .requiredSize(112.dp)
                    .graphicsLayer {
                        alpha = 1f - iconProgress
                        scaleX = 1f - (iconProgress * 0.18f)
                        scaleY = 1f - (iconProgress * 0.18f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (
                            lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0
                        ) {
                            iconBottomPx = coordinates.positionInRoot().y + coordinates.size.height
                        }
                    },
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                    modifier = Modifier
                        .requiredSize(200.dp)
                        .textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(24.dp),
                            blurRadius = 200f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(blendColors = logoBlend),
                            contentBlendMode = BlendMode.DstIn,
                            enabled = blurEnable,
                        ),
                    contentDescription = stringResource(id = R.string.app_name)
                )
            }

            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        alpha = 1f - projectNameProgress
                        scaleX = 1f - (projectNameProgress * 0.14f)
                        scaleY = 1f - (projectNameProgress * 0.14f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (
                            lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0
                        ) {
                            projectNameBottomPx = coordinates.positionInRoot().y + coordinates.size.height
                        }
                    }
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 150f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = BlurColors(blendColors = logoBlend),
                        contentBlendMode = BlendMode.DstIn,
                        enabled = blurEnable,
                    ),
                text = stringResource(id = R.string.app_name),
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1f - versionCodeProgress
                        scaleX = 1f - (versionCodeProgress * 0.1f)
                        scaleY = 1f - (versionCodeProgress * 0.1f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (
                            lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0
                        ) {
                            versionCodeBottomPx = coordinates.positionInRoot().y + coordinates.size.height
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    text = stringResource(
                        id = R.string.about_blend_version,
                        versionName,
                        versionCode
                    ),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Scrollable content area
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> listViewportHeightPx = size.height }
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = listContentPadding,
            overscrollEffect = null
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp +
                                    logoPadding.calculateTopPadding() -
                                    listContentPadding.calculateTopPadding() + 126.dp
                        )
                        .onSizeChanged { size -> onLogoHeightChanged(size.height) }
                        .onGloballyPositioned { coordinates ->
                            if (
                                lazyListState.firstVisibleItemIndex == 0 &&
                                lazyListState.firstVisibleItemScrollOffset == 0
                            ) {
                                logoAreaBottomPx = coordinates.positionInRoot().y + coordinates.size.height
                            }
                        }
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .onSizeChanged { size -> actionCardHeightPx = size.height }
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(16.dp),
                            blurRadius = 60f,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurColors(
                                blendColors = currentConfigValue,
                                brightness = 0f,
                                contrast = 1f,
                                saturation = 1.5f,
                            ),
                            enabled = blurEnable,
                        ),
                    colors = CardDefaults.defaultColors(
                        if (blurEnable) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                        Color.Transparent,
                    ),
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.about_github),
                        summary = stringResource(R.string.about_github_desc),
                        onClick = {
                            uriHandler.openUri("https://github.com/Inefy-03/IconEditor")
                        }
                    )
                }
            }
        }
    }

    BgEffectBackground(
        dynamicBackground = dynamicBackground,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        effectBackground = effectBackground,
        alpha = { 1f - backgroundProgress },
        isOs3Effect = isOs3Effect,
        content = { bodyContent() }
    )
}

private fun collapseProgress(
    value: Float,
    start: Float,
    end: Float,
): Float {
    val linearProgress = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return linearProgress * linearProgress * (3f - 2f * linearProgress)
}
