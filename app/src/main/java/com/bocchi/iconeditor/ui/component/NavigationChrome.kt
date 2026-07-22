package com.bocchi.iconeditor.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bocchi.iconeditor.model.AppSettings
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.IconPreferences
import com.bocchi.iconeditor.model.InfoTab
import com.bocchi.iconeditor.model.ProjectSortField
import com.bocchi.iconeditor.model.SortField
import com.bocchi.iconeditor.model.SpecialAssetsTab
import com.bocchi.iconeditor.model.ThemeMode
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.anim.SinOutEasing
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollHorizontal

@Serializable
enum class Screen : NavKey {
    Projects,
    Settings,
    Info,
    Icons,
    SpecialAssets,
    ThemeSettings,
    About,
    ProjectSync,
    Trash,
}

@Composable
fun appPageBackground(settings: AppSettings): Color {
    val dark = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    return if (dark) Color.Black else MiuixTheme.colorScheme.surface
}

@Composable
fun PaddingValues.withEdgeToEdgeBottom(hasBottomBar: Boolean, ignoreTop: Boolean = false): PaddingValues {
    if (hasBottomBar && !ignoreTop) return this
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = if (ignoreTop) 0.dp else calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = if (hasBottomBar) calculateBottomPadding() else 0.dp,
    )
}

@Composable
fun PaddingValues.withPageMargins(horizontal: Dp = 12.dp, vertical: Dp = 12.dp): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + horizontal,
        top = calculateTopPadding() + vertical,
        end = calculateEndPadding(layoutDirection) + horizontal,
        bottom = calculateBottomPadding() + vertical,
    )
}

@Composable
fun RootPagerContent(
    pagerState: PagerState,
    pageBackground: Color,
    floatingBottomBar: Boolean,
    floatingBackdrop: LayerBackdrop,
    projectsPage: @Composable () -> Unit,
    settingsPage: @Composable () -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        overscrollEffect = null,
        verticalAlignment = Alignment.Top,
        beyondViewportPageCount = 1,
        modifier = Modifier
            .fillMaxSize()
            .overScrollHorizontal()
            .then(
                if (floatingBottomBar) {
                    Modifier.layerBackdrop(floatingBackdrop)
                } else {
                    Modifier
                }
            )
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBackground),
        ) {
            when (page) {
                0 -> projectsPage()
                1 -> settingsPage()
            }
        }
    }
}

fun Screen.isRoot(): Boolean = this == Screen.Projects || this == Screen.Settings

fun rootScreenIndex(screen: Screen): Int = when (screen) {
    Screen.Projects -> 0
    Screen.Settings -> 1
    else -> 0
}

@Composable
fun navigationBarBottomPadding(): Dp {
    return WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
}

@Composable
fun pageBottomContentPadding(extra: Dp = 16.dp): PaddingValues {
    return PaddingValues(bottom = navigationBarBottomPadding() + extra)
}

@Composable
fun AppTopBar(
    screen: Screen,
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    scrollBehavior: ScrollBehavior,
    onBack: () -> Unit,
    onCreateProject: () -> Unit,
    onImportProject: () -> Unit = {},
    iconPreferences: IconPreferences,
    onIconPreferences: (IconPreferences) -> Unit,
    projectSortField: ProjectSortField = ProjectSortField.CreatedAt,
    onProjectSortField: (ProjectSortField) -> Unit = {},
    infoTab: InfoTab = InfoTab.Mtz,
    onInfoTab: (InfoTab) -> Unit = {},
    specialAssetsTab: SpecialAssetsTab = SpecialAssetsTab.Masks,
    onSpecialAssetsTab: (SpecialAssetsTab) -> Unit = {},
    onImportIcons: () -> Unit = {},
    onAddIcon: () -> Unit = {},
) {
    var showIconSortMenu by remember { mutableStateOf(false) }
    var showIconFilterMenu by remember { mutableStateOf(false) }
    var showIconOptionsMenu by remember { mutableStateOf(false) }
    val title = when (screen) {
        Screen.Projects -> stringResource(R.string.screen_projects)
        Screen.Settings -> stringResource(R.string.screen_settings)
        Screen.Info -> stringResource(R.string.screen_info_edit)
        Screen.Icons -> stringResource(R.string.screen_icon_edit)
        Screen.SpecialAssets -> stringResource(R.string.screen_special_assets)
        Screen.ThemeSettings -> stringResource(R.string.screen_theme_settings)
        Screen.About -> stringResource(R.string.about_title)
        Screen.ProjectSync -> stringResource(R.string.screen_project_sync)
        Screen.Trash -> stringResource(R.string.screen_trash)
    }
    if (screen == Screen.About) return
    TopAppBar(
        title = title,
        largeTitle = title,
        modifier = Modifier.miuixBarBlur(backdrop, blurEnabled),
        color = backdrop.getMiuixAppBarColor(),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (!screen.isRoot()) {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = {
            when (screen) {
                Screen.Projects -> {
                    ProjectSortAction(
                        sortField = projectSortField,
                        onSortField = onProjectSortField,
                    )
                    ProjectMenuAction(
                        onCreateProject = onCreateProject,
                        onImportProject = onImportProject,
                    )
                }
                Screen.Icons -> {
                    Box {
                        IconButton(
                            onClick = { showIconSortMenu = true },
                            holdDownState = showIconSortMenu,
                        ) {
                            Icon(MiuixIcons.Sort, contentDescription = stringResource(R.string.action_sort))
                        }
                        OverlayListPopup(
                            show = showIconSortMenu,
                            popupPositionProvider = ListPopupDefaults.dropdownPositionProvider(verticalMargin = 0.dp),
                            alignment = PopupPositionProvider.Align.End,
                            enableWindowDim = true,
                            onDismissRequest = { showIconSortMenu = false },
                        ) {
                            IconSortMenu(
                                preferences = iconPreferences,
                                onPreferences = onIconPreferences,
                                onDismiss = { showIconSortMenu = false },
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showIconFilterMenu = true },
                            holdDownState = showIconFilterMenu,
                        ) {
                            Icon(MiuixIcons.Filter, contentDescription = stringResource(R.string.action_filter))
                        }
                        OverlayListPopup(
                            show = showIconFilterMenu,
                            popupPositionProvider = ListPopupDefaults.dropdownPositionProvider(verticalMargin = 0.dp),
                            alignment = PopupPositionProvider.Align.End,
                            enableWindowDim = true,
                            onDismissRequest = { showIconFilterMenu = false },
                        ) {
                            IconFilterMenu(
                                preferences = iconPreferences,
                                onPreferences = onIconPreferences,
                                onDismiss = { showIconFilterMenu = false },
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showIconOptionsMenu = true },
                            holdDownState = showIconOptionsMenu,
                        ) {
                            Icon(MiuixIcons.MoreCircle, contentDescription = stringResource(R.string.action_more))
                        }
                        OverlayListPopup(
                            show = showIconOptionsMenu,
                            popupPositionProvider = ListPopupDefaults.dropdownPositionProvider(verticalMargin = 0.dp),
                            alignment = PopupPositionProvider.Align.End,
                            enableWindowDim = true,
                            onDismissRequest = { showIconOptionsMenu = false },
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    item = DropdownItem(text = stringResource(R.string.action_add_icon)),
                                    optionSize = IconOptionsMenuCount,
                                    isSelected = false,
                                    index = 0,
                                    isFirst = true,
                                    isLast = false,
                                    onSelectedIndexChange = {
                                        showIconOptionsMenu = false
                                        onAddIcon()
                                    },
                                )
                                DropdownImpl(
                                    item = DropdownItem(text = stringResource(R.string.action_import_icons)),
                                    optionSize = IconOptionsMenuCount,
                                    isSelected = false,
                                    index = 1,
                                    isFirst = false,
                                    isLast = true,
                                    onSelectedIndexChange = {
                                        showIconOptionsMenu = false
                                        onImportIcons()
                                    },
                                )
                            }
                        }
                    }
                }
                else -> Unit
            }
        },
        bottomContent = {
            when (screen) {
                Screen.Icons -> IconSearchBar(
                    preferences = iconPreferences,
                    onPreferences = onIconPreferences,
                )
                Screen.Info -> InfoTabRow(
                    selectedTab = infoTab,
                    onSelectedTab = onInfoTab,
                )
                Screen.SpecialAssets -> SpecialAssetsTabRow(
                    selectedTab = specialAssetsTab,
                    onSelectedTab = onSpecialAssetsTab,
                )
                else -> Unit
            }
        },
    )
}

@Composable
private fun SpecialAssetsTabRow(
    selectedTab: SpecialAssetsTab,
    onSelectedTab: (SpecialAssetsTab) -> Unit,
) {
    val masksTitle = stringResource(R.string.tab_icon_masks)
    val specialIconsTitle = stringResource(R.string.tab_special_icons)
    val tabs = remember(masksTitle, specialIconsTitle) { listOf(masksTitle, specialIconsTitle) }
    TabRow(
        tabs = tabs,
        selectedTabIndex = selectedTab.ordinal,
        onTabSelected = { index -> onSelectedTab(SpecialAssetsTab.entries[index]) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun InfoTabRow(
    selectedTab: InfoTab,
    onSelectedTab: (InfoTab) -> Unit,
) {
    val mtzTitle = stringResource(R.string.tab_mtz_info)
    val moduleTitle = stringResource(R.string.tab_module_info)
    val apkTitle = stringResource(R.string.tab_apk_info)
    val tabs = remember(mtzTitle, moduleTitle, apkTitle) { listOf(mtzTitle, moduleTitle, apkTitle) }
    TabRow(
        tabs = tabs,
        selectedTabIndex = selectedTab.ordinal,
        onTabSelected = { index -> onSelectedTab(InfoTab.entries[index]) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun IconSearchBar(
    preferences: IconPreferences,
    onPreferences: (IconPreferences) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SearchBar(
        inputField = {
            InputField(
                query = preferences.search,
                onQueryChange = { onPreferences(preferences.copy(search = it)) },
                onSearch = { expanded = false },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                label = stringResource(R.string.search_apps_hint),
            )
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        outsideEndAction = {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(180, easing = SinOutEasing)) +
                    expandHorizontally(animationSpec = tween(180, easing = SinOutEasing)),
                exit = fadeOut(animationSpec = tween(140, easing = SinOutEasing)) +
                    shrinkHorizontally(animationSpec = tween(140, easing = SinOutEasing)),
            ) {
                Text(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                        ) {
                            expanded = false
                            onPreferences(preferences.copy(search = ""))
                        },
                    text = stringResource(R.string.action_cancel),
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
    ) {}
}

@Composable
private fun ProjectSortAction(
    sortField: ProjectSortField,
    onSortField: (ProjectSortField) -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { showSortMenu = true },
            holdDownState = showSortMenu,
        ) {
            Icon(MiuixIcons.Sort, contentDescription = stringResource(R.string.action_sort))
        }
        OverlayListPopup(
            show = showSortMenu,
            popupPositionProvider = ListPopupDefaults.dropdownPositionProvider(verticalMargin = 0.dp),
            alignment = PopupPositionProvider.Align.End,
            enableWindowDim = true,
            onDismissRequest = { showSortMenu = false },
        ) {
            ListPopupColumn {
                ProjectSortField.entries.forEachIndexed { index, field ->
                    val title = when (field) {
                        ProjectSortField.CreatedAt -> stringResource(R.string.sort_project_created)
                        ProjectSortField.UpdatedAt -> stringResource(R.string.sort_project_modified)
                        ProjectSortField.Name -> stringResource(R.string.sort_project_name)
                    }
                    DropdownImpl(
                        item = DropdownItem(text = title),
                        optionSize = ProjectSortOptionCount,
                        isSelected = sortField == field,
                        index = index,
                        isFirst = index == 0,
                        isLast = index == ProjectSortField.entries.lastIndex,
                        onSelectedIndexChange = {
                            onSortField(field)
                            showSortMenu = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectMenuAction(
    onCreateProject: () -> Unit,
    onImportProject: () -> Unit,
) {
    var showProjectMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showProjectMenu = true }) {
            Icon(MiuixIcons.MoreCircle, contentDescription = stringResource(R.string.action_more))
        }
        OverlayListPopup(
            show = showProjectMenu,
            popupPositionProvider = ListPopupDefaults.dropdownPositionProvider(verticalMargin = 0.dp),
            alignment = PopupPositionProvider.Align.End,
            enableWindowDim = true,
            onDismissRequest = { showProjectMenu = false },
        ) {
            ListPopupColumn {
                DropdownImpl(
                    item = DropdownItem(text = stringResource(R.string.new_project)),
                    optionSize = ProjectOptionsMenuCount,
                    isSelected = false,
                    index = 0,
                    isFirst = true,
                    isLast = false,
                    onSelectedIndexChange = {
                        showProjectMenu = false
                        onCreateProject()
                    },
                )
                DropdownImpl(
                    item = DropdownItem(text = stringResource(R.string.action_import_project)),
                    optionSize = ProjectOptionsMenuCount,
                    isSelected = false,
                    index = 1,
                    isFirst = false,
                    isLast = true,
                    onSelectedIndexChange = {
                        showProjectMenu = false
                        onImportProject()
                    },
                )
            }
        }
    }
}

@Composable
private fun IconSortMenu(
    preferences: IconPreferences,
    onPreferences: (IconPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    ListPopupColumn {
        DropdownImpl(
            item = DropdownItem(text = stringResource(R.string.sort_app_name)),
            optionSize = IconSortOptionCount,
            isSelected = preferences.sortField == SortField.AppName,
            index = 0,
            isFirst = true,
            isLast = false,
            onSelectedIndexChange = {
                onPreferences(preferences.copy(sortField = SortField.AppName))
                onDismiss()
            },
        )
        DropdownImpl(
            item = DropdownItem(text = stringResource(R.string.sort_package_name)),
            optionSize = IconSortOptionCount,
            isSelected = preferences.sortField == SortField.PackageName,
            index = 1,
            isFirst = false,
            isLast = false,
            onSelectedIndexChange = {
                onPreferences(preferences.copy(sortField = SortField.PackageName))
                onDismiss()
            },
        )
        PopupGroupDivider()
        DropdownImpl(
            item = DropdownItem(text = stringResource(R.string.sort_descending)),
            optionSize = IconSortOptionCount,
            isSelected = preferences.descending,
            index = 2,
            isFirst = false,
            isLast = true,
            onSelectedIndexChange = {
                onPreferences(preferences.copy(descending = !preferences.descending))
                onDismiss()
            },
        )
    }
}

@Composable
private fun IconFilterMenu(
    preferences: IconPreferences,
    onPreferences: (IconPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    ListPopupColumn {
        DropdownImpl(
            item = DropdownItem(text = stringResource(R.string.show_local_apps)),
            optionSize = IconFilterOptionCount,
            isSelected = preferences.showLocalApps,
            index = 0,
            isFirst = true,
            isLast = false,
            onSelectedIndexChange = {
                onPreferences(preferences.copy(showLocalApps = !preferences.showLocalApps))
                onDismiss()
            },
        )
        DropdownImpl(
            item = DropdownItem(text = stringResource(R.string.show_system_apps)),
            optionSize = IconFilterOptionCount,
            isSelected = preferences.showSystemApps,
            index = 1,
            isFirst = false,
            isLast = false,
            onSelectedIndexChange = {
                onPreferences(preferences.copy(showSystemApps = !preferences.showSystemApps))
                onDismiss()
            },
        )
        PopupGroupDivider()
        DropdownImpl(
            item = DropdownItem(text = stringResource(R.string.only_show_multiple_styles)),
            optionSize = IconFilterOptionCount,
            isSelected = preferences.onlyShowMultipleStyles,
            index = 2,
            isFirst = false,
            isLast = false,
            onSelectedIndexChange = {
                onPreferences(
                    preferences.copy(
                        onlyShowMultipleStyles = !preferences.onlyShowMultipleStyles,
                        onlyShowUnadaptedIcons = false,
                    ),
                )
                onDismiss()
            },
        )
        DropdownImpl(
            item = DropdownItem(text = stringResource(R.string.only_show_unadapted_icons)),
            optionSize = IconFilterOptionCount,
            isSelected = preferences.onlyShowUnadaptedIcons,
            index = 3,
            isFirst = false,
            isLast = true,
            onSelectedIndexChange = {
                onPreferences(
                    preferences.copy(
                        onlyShowUnadaptedIcons = !preferences.onlyShowUnadaptedIcons,
                        onlyShowMultipleStyles = false,
                    ),
                )
                onDismiss()
            },
        )
    }
}

@Composable
private fun PopupGroupDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

private const val IconSortOptionCount = 3
private const val IconFilterOptionCount = 4
private const val IconOptionsMenuCount = 2
private const val ProjectSortOptionCount = 3
private const val ProjectOptionsMenuCount = 2

@Composable
fun MainBottomBar(
    pagerState: PagerState,
    onSelect: (Screen) -> Unit,
    settings: AppSettings,
    backdrop: LayerBackdrop?,
    floatingBackdrop: LayerBackdrop,
) {
    if (settings.floatingBottomBar) {
        val projectsLabel = stringResource(R.string.screen_projects)
        val settingsLabel = stringResource(R.string.screen_settings)
        val items = remember(projectsLabel, settingsLabel) {
            listOf(
                Screen.Projects to (projectsLabel to MiuixIcons.Folder),
                Screen.Settings to (settingsLabel to MiuixIcons.Settings),
            )
        }
        val mode = when {
            settings.liquidGlass && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU ->
                FloatingBottomBarMode.LiquidGlass
            settings.blurEnabled -> FloatingBottomBarMode.Blur
            else -> FloatingBottomBarMode.None
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            FloatingBottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(bottom = 12.dp + navigationBarBottomPadding()),
                selectedIndex = { pagerState.currentPage },
                onSelected = { index -> onSelect(items[index].first) },
                backdrop = floatingBackdrop,
                tabsCount = items.size,
                mode = mode,
            ) {
                items.forEach { (screen, item) ->
                    FloatingBottomBarItem(
                        onClick = { onSelect(screen) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                    ) {
                        Icon(imageVector = item.second, contentDescription = item.first)
                        Text(
                            text = item.first,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    } else {
        val barColor = backdrop.getMiuixAppBarColor()
        val selected = if (pagerState.currentPage == rootScreenIndex(Screen.Settings)) Screen.Settings else Screen.Projects
        Box(
            modifier = Modifier
                .miuixBarBlur(backdrop, settings.blurEnabled)
                .background(barColor),
        ) {
            NavigationBar(color = barColor) {
                MainBottomBarItems(selected, onSelect)
            }
        }
    }
}

@Composable
fun RowScope.MainBottomBarItems(selected: Screen, onSelect: (Screen) -> Unit) {
    NavigationBarItem(
        selected = selected == Screen.Projects,
        onClick = { onSelect(Screen.Projects) },
        icon = MiuixIcons.Folder,
        label = stringResource(R.string.screen_projects),
    )
    NavigationBarItem(
        selected = selected == Screen.Settings,
        onClick = { onSelect(Screen.Settings) },
        icon = MiuixIcons.Settings,
        label = stringResource(R.string.screen_settings),
    )
}

@Composable
fun MainNavigationRail(
    selected: Screen,
    settings: AppSettings,
    backdrop: LayerBackdrop?,
    onSelect: (Screen) -> Unit,
) {
    val railColor = backdrop.getMiuixAppBarColor()
    Box(
        modifier = Modifier
            .miuixBarBlur(backdrop, settings.blurEnabled)
            .background(railColor),
    ) {
        NavigationRail(
            color = railColor,
            showDivider = true,
        ) {
            NavigationRailItem(
                selected = selected == Screen.Projects,
                onClick = { onSelect(Screen.Projects) },
                icon = MiuixIcons.Folder,
                label = stringResource(R.string.screen_projects),
            )
            NavigationRailItem(
                selected = selected == Screen.Settings,
                onClick = { onSelect(Screen.Settings) },
                icon = MiuixIcons.Settings,
                label = stringResource(R.string.screen_settings),
            )
        }
    }
}

@Composable
fun rememberMiuixBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported() || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun LayerBackdrop?.getMiuixAppBarColor(): Color = this?.let { Color.Transparent } ?: MiuixTheme.colorScheme.surface

@Composable
fun Modifier.miuixBarBlur(backdrop: LayerBackdrop?, enabled: Boolean = true): Modifier {
    if (!enabled || backdrop == null) return this
    val blendColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)
    return then(
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = 25f,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(blendColor),
                ),
            ),
        ),
    )
}
