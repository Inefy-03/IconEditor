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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.blend.ColorBlendToken
import com.bocchi.iconeditor.effect.BgEffectBackground
import com.bocchi.iconeditor.ui.theme.isIconEditorDark
import kotlinx.coroutines.flow.onEach
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var logoHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.about_title),
                scrollBehavior = scrollBehavior,
                color = MiuixTheme.colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f),
                titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(id = R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        AboutContentBody(
            padding = innerPadding,
            lazyListState = lazyListState,
            scrollProgress = scrollProgress,
            scrollBehavior = scrollBehavior,
            onLogoHeightChanged = { logoHeightPx = it }
        )
    }
}

@Composable
private fun AboutContentBody(
    padding: PaddingValues,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    scrollProgress: Float,
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

    // Animation states
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var iconY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }

    var iconProgress by remember { mutableFloatStateOf(0f) }
    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .onEach { offset ->
                if (lazyListState.firstVisibleItemIndex > 0) {
                    if (iconProgress != 1f) iconProgress = 1f
                    if (projectNameProgress != 1f) projectNameProgress = 1f
                    if (versionCodeProgress != 1f) versionCodeProgress = 1f
                    return@onEach
                }

                if (initialLogoAreaY == 0f && logoAreaY > 0f) initialLogoAreaY = logoAreaY
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY

                val stage1TotalLength = refLogoAreaY - versionCodeY
                val stage2TotalLength = versionCodeY - projectNameY
                val stage3TotalLength = projectNameY - iconY

                val versionCodeDelay = stage1TotalLength * 0.5f
                versionCodeProgress = ((offset.toFloat() - versionCodeDelay) / (stage1TotalLength - versionCodeDelay).coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                projectNameProgress = ((offset.toFloat() - stage1TotalLength) / stage2TotalLength.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                iconProgress = ((offset.toFloat() - stage1TotalLength - stage2TotalLength) / stage3TotalLength.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
            }
            .collect {}
    }

    val displayCutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()

    val listContentPadding = PaddingValues(
        start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateStartPadding(layoutDirection),
        top = padding.calculateTopPadding(),
        end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateEndPadding(layoutDirection),
        bottom = padding.calculateBottomPadding()
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
                        alpha = 1 - iconProgress
                        scaleX = 1 - (iconProgress * 0.05f)
                        scaleY = 1 - (iconProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (iconY == 0f) {
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            iconY = y + size.height
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
                    .onGloballyPositioned { coordinates ->
                        if (projectNameY == 0f) {
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            projectNameY = y + size.height
                        }
                    }
                    .graphicsLayer {
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
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
                        alpha = 1 - versionCodeProgress
                        scaleX = 1 - (versionCodeProgress * 0.05f)
                        scaleY = 1 - (versionCodeProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionCodeY == 0f) {
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            versionCodeY = y + size.height
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
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            logoAreaY = y + size.height
                        }
                )
            }

            item {
                Card(
                    modifier = Modifier
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
        alpha = { 1f - scrollProgress },
        isOs3Effect = isOs3Effect,
        content = { bodyContent() }
    )
}
