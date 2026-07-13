package com.bocchi.iconeditor.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import com.bocchi.iconeditor.model.AppSettings
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.model.ExportFormat
import com.bocchi.iconeditor.model.IconImportMode
import com.bocchi.iconeditor.model.IconPreferences
import com.bocchi.iconeditor.model.InfoTab
import com.bocchi.iconeditor.model.ProjectSummary
import com.bocchi.iconeditor.model.ThemeMode
import com.bocchi.iconeditor.state.AppViewModel
import com.bocchi.iconeditor.ui.component.AppTopBar
import com.bocchi.iconeditor.ui.component.ConfirmDeleteDialog
import com.bocchi.iconeditor.ui.component.ExportDialog
import com.bocchi.iconeditor.ui.component.ExportValidationDialog
import com.bocchi.iconeditor.ui.component.IconImportConfirmDialog
import com.bocchi.iconeditor.ui.component.MainBottomBar
import com.bocchi.iconeditor.ui.component.MainNavigationRail
import com.bocchi.iconeditor.ui.component.MaskLayerImportConfirmDialog
import com.bocchi.iconeditor.ui.component.MessageDialog
import com.bocchi.iconeditor.ui.component.RootPagerContent
import com.bocchi.iconeditor.ui.component.Screen
import com.bocchi.iconeditor.ui.component.appPageBackground
import com.bocchi.iconeditor.data.ApkPackAssets
import com.bocchi.iconeditor.data.ExportDirectoryHelper
import com.bocchi.iconeditor.data.ImportSourceDetector
import com.bocchi.iconeditor.ui.component.rememberMiuixBlurBackdrop
import com.bocchi.iconeditor.ui.component.rootScreenIndex
import com.bocchi.iconeditor.ui.component.withPageMargins
import com.bocchi.iconeditor.ui.navigation.PredictiveNavDisplay
import com.bocchi.iconeditor.ui.locale.ensureInitialAppLanguage
import com.bocchi.iconeditor.ui.page.AboutPage
import com.bocchi.iconeditor.ui.page.IconEditPage
import com.bocchi.iconeditor.ui.page.InfoEditPage
import com.bocchi.iconeditor.ui.page.ProjectsPage
import com.bocchi.iconeditor.ui.page.SettingsPage
import com.bocchi.iconeditor.ui.page.ThemeSettingsPage
import kotlinx.coroutines.launch
import com.bocchi.iconeditor.ui.component.displayName
import top.yukonga.miuix.kmp.basic.FabPosition
import com.bocchi.iconeditor.ui.component.ExportProgressOverlay
import com.bocchi.iconeditor.ui.component.ImportProgressOverlay
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

internal val ProjectImportMimeTypes = listOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/x-miui-theme",
    "application/vnd.android.package-archive",
)
internal const val ProjectImportPrimaryMimeType = "application/zip"
private const val InstalledAppsPermission = "com.android.permission.GET_INSTALLED_APPS"

private enum class ApkAssetPickTarget {
    LauncherIcon,
    IconBack,
    IconMask,
    IconUpon,
}

private class OpenProjectDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, input)
        }
}

private class CreateExportDocument(
    private val mimeType: String,
    private val initialDirectory: Uri?,
) : ActivityResultContracts.CreateDocument(mimeType) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).apply {
            initialDirectory?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
}

class MainActivity : ComponentActivity() {
    private var incomingProjectUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingProjectUri = intent.projectUri()
        ensureInitialAppLanguage(this)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel()
            val mode = when (viewModel.settings.themeMode) {
                ThemeMode.System -> ColorSchemeMode.System
                ThemeMode.Light -> ColorSchemeMode.Light
                ThemeMode.Dark -> ColorSchemeMode.Dark
            }
            val controller = remember(mode) { ThemeController(colorSchemeMode = mode) }
            MiuixTheme(controller = controller) {
                IconEditorApp(
                    viewModel = viewModel,
                    incomingProjectUri = incomingProjectUri,
                    onIncomingProjectHandled = ::clearIncomingProject,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingProjectUri = intent.projectUri()
    }

    private fun clearIncomingProject() {
        incomingProjectUri = null
        setIntent(Intent(Intent.ACTION_MAIN))
    }
}

@Composable
private fun IconEditorApp(
    viewModel: AppViewModel,
    incomingProjectUri: Uri?,
    onIncomingProjectHandled: () -> Unit,
) {
    var deleteProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var exportProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var exportPickerProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var incompleteExport by remember { mutableStateOf<Pair<ProjectSummary, ExportFormat>?>(null) }
    var infoTab by remember { mutableStateOf(InfoTab.Mtz) }
    var rootTabIndex by rememberSaveable { mutableStateOf(rootScreenIndex(Screen.Projects)) }
    val navBackStack = rememberNavBackStack(Screen.Projects)
    val miuixBackdrop = rememberMiuixBlurBackdrop(viewModel.settings.blurEnabled)
    val floatingBackdrop = rememberLayerBackdrop()
    val rootScreens = remember { listOf(Screen.Projects, Screen.Settings) }
    val rootPagerState = rememberPagerState(
        initialPage = rootTabIndex.coerceIn(rootScreens.indices),
        pageCount = { rootScreens.size },
    )
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val installedAppsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.preloadInstalledApps(forceRefresh = true)
        if (!granted) {
            viewModel.notifyInstalledAppsPermissionDenied()
        }
    }
    fun requestInstalledAppsAccessIfNeeded() {
        if (context.requiresInstalledAppsPermission() &&
            context.checkSelfPermission(InstalledAppsPermission) != PackageManager.PERMISSION_GRANTED
        ) {
            installedAppsPermissionLauncher.launch(InstalledAppsPermission)
        } else {
            viewModel.preloadInstalledApps()
        }
    }
    fun onIconPreferencesChanged(preferences: IconPreferences) {
        val enablingInstalledApps =
            (!viewModel.iconPreferences.showLocalApps && preferences.showLocalApps) ||
                (!viewModel.iconPreferences.showSystemApps && preferences.showSystemApps)
        viewModel.updateIconPreferences(preferences)
        if (enablingInstalledApps &&
            context.requiresInstalledAppsPermission() &&
            context.checkSelfPermission(InstalledAppsPermission) != PackageManager.PERMISSION_GRANTED
        ) {
            installedAppsPermissionLauncher.launch(InstalledAppsPermission)
        }
    }
    LaunchedEffect(context, viewModel) {
        requestInstalledAppsAccessIfNeeded()
    }
    val importLauncher = rememberLauncherForActivityResult(OpenProjectDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importProject(it, ImportSourceDetector.resolveDisplayName(context, it))
        }
    }
    val iconPackImportLauncher = rememberLauncherForActivityResult(OpenProjectDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.previewIconsFromPack(it)
        }
    }
    val maskPackImportLauncher = rememberLauncherForActivityResult(OpenProjectDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.previewMaskLayersFromPack(it)
        }
    }
    val mtzExportLauncher = rememberLauncherForActivityResult(
        CreateExportDocument(
            mimeType = "application/octet-stream",
            initialDirectory = ExportDirectoryHelper.initialDocumentUri(viewModel.settings),
        ),
    ) { uri ->
        val project = exportPickerProject
        if (uri != null && project != null) viewModel.exportProject(project.id, ExportFormat.Mtz, uri)
        exportPickerProject = null
    }
    val zipExportLauncher = rememberLauncherForActivityResult(
        CreateExportDocument(
            mimeType = "application/zip",
            initialDirectory = ExportDirectoryHelper.initialDocumentUri(viewModel.settings),
        ),
    ) { uri ->
        val project = exportPickerProject
        if (uri != null && project != null) viewModel.exportProject(project.id, ExportFormat.ModuleZip, uri)
        exportPickerProject = null
    }
    val apkExportLauncher = rememberLauncherForActivityResult(
        CreateExportDocument(
            mimeType = "application/vnd.android.package-archive",
            initialDirectory = ExportDirectoryHelper.initialDocumentUri(viewModel.settings),
        ),
    ) { uri ->
        val project = exportPickerProject
        if (uri != null && project != null) viewModel.exportProject(project.id, ExportFormat.Apk, uri)
        exportPickerProject = null
    }
    var apkAssetPickTarget by remember { mutableStateOf<ApkAssetPickTarget?>(null) }
    val apkAssetPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val target = apkAssetPickTarget
        apkAssetPickTarget = null
        if (uri != null && target != null) {
            when (target) {
                ApkAssetPickTarget.LauncherIcon -> viewModel.setApkLauncherIcon(uri)
                ApkAssetPickTarget.IconBack -> viewModel.setApkMaskLayer(ApkPackAssets.MaskLayer.Back, uri)
                ApkAssetPickTarget.IconMask -> viewModel.setApkMaskLayer(ApkPackAssets.MaskLayer.Mask, uri)
                ApkAssetPickTarget.IconUpon -> viewModel.setApkMaskLayer(ApkPackAssets.MaskLayer.Upon, uri)
            }
        }
    }
    val scrollBehavior = MiuixScrollBehavior()
    LaunchedEffect(rootPagerState) {
        snapshotFlow { rootPagerState.currentPage }.collect { page ->
            val safePage = page.coerceIn(rootScreens.indices)
            rootTabIndex = safePage
        }
    }

    fun navigateTo(target: Screen) {
        if (navBackStack.lastOrNull() != target) navBackStack.add(target)
    }

    fun selectRoot(target: Screen) {
        val page = rootScreenIndex(target)
        while (navBackStack.size > 1) navBackStack.removeLastOrNull()
        rootTabIndex = page
        coroutineScope.launch {
            rootPagerState.animateScrollToPage(page)
        }
    }

    fun navigateBack() {
        if (navBackStack.size > 1) navBackStack.removeLastOrNull()
    }

    LaunchedEffect(incomingProjectUri) {
        incomingProjectUri?.let { uri ->
            selectRoot(Screen.Projects)
            viewModel.importProject(uri, ImportSourceDetector.resolveDisplayName(context, uri))
            onIncomingProjectHandled()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 720.dp
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
        ) {
            Box(Modifier.fillMaxSize()) {
                PredictiveNavDisplay(
                    backStack = navBackStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = ::navigateBack,
                    predictiveBackEnabled = viewModel.settings.predictiveBackEnabled,
                    entryProvider = { targetScreen ->
                        NavEntry(targetScreen) {
                            val pageBackground = appPageBackground(viewModel.settings)
                            when (targetScreen) {
                                Screen.Projects, Screen.Settings -> RootScene(
                                    rootScreens = rootScreens,
                                    useNavigationRail = useNavigationRail,
                                    pageBackground = pageBackground,
                                    settings = viewModel.settings,
                                    pagerState = rootPagerState,
                                    miuixBackdrop = miuixBackdrop,
                                    floatingBackdrop = floatingBackdrop,
                                    iconPreferences = viewModel.iconPreferences,
                                    onIconPreferences = ::onIconPreferencesChanged,
                                    onSettings = viewModel::updateSettings,
                                    onCreateProject = viewModel::createProject,
                                    onImportProject = {
                                        importLauncher.launch(ProjectImportMimeTypes.toTypedArray())
                                    },
                                    onSelectRoot = ::selectRoot,
                                    projectsPage = { contentPadding ->
                                        ProjectsPage(
                                            projects = viewModel.projects,
                                            metadata = viewModel.projectMetadata,
                                            sortField = viewModel.settings.projectSortField,
                                            contentPadding = contentPadding.withPageMargins(),
                                            scrollToTopRequest = viewModel.projectsScrollToTopRequest,
                                            onEditInfo = {
                                                infoTab = InfoTab.Mtz
                                                viewModel.loadProject(it.id, loadIcons = false)
                                                navigateTo(Screen.Info)
                                            },
                                            onEditIcons = {
                                                viewModel.loadProject(it.id, loadIcons = true)
                                                navigateTo(Screen.Icons)
                                            },
                                            onDelete = { deleteProject = it },
                                            onExport = { exportProject = it },
                                        )
                                    },
                                    settingsPage = { contentPadding ->
                                        SettingsPage(
                                            settings = viewModel.settings,
                                            contentPadding = contentPadding.withPageMargins(horizontal = 0.dp),
                                            onSettings = viewModel::updateSettings,
                                            onTheme = { navigateTo(Screen.ThemeSettings) },
                                            onAbout = { navigateTo(Screen.About) },
                                        )
                                    },
                                )
                                Screen.Info -> SecondaryScene(
                                    screen = Screen.Info,
                                    pageBackground = pageBackground,
                                    scrollBehavior = scrollBehavior,
                                    blurEnabled = viewModel.settings.blurEnabled,
                                    iconPreferences = viewModel.iconPreferences,
                                    onIconPreferences = ::onIconPreferencesChanged,
                                    onBack = ::navigateBack,
                                    onCreateProject = viewModel::createProject,
                                    infoTab = infoTab,
                                    onInfoTab = { infoTab = it },
                                ) { contentPadding ->
                                    InfoEditPage(
                                        metadata = viewModel.metadata,
                                        selectedTab = infoTab,
                                        contentPadding = contentPadding,
                                        onSaveMtz = viewModel::saveMtzInfo,
                                        onSaveModule = viewModel::saveModuleInfo,
                                        onSaveApk = viewModel::saveApkInfo,
                                        launcherIconFile = viewModel.apkLauncherIconFile(),
                                        iconBackFile = viewModel.apkMaskLayerFile(ApkPackAssets.MaskLayer.Back),
                                        iconMaskFile = viewModel.apkMaskLayerFile(ApkPackAssets.MaskLayer.Mask),
                                        iconUponFile = viewModel.apkMaskLayerFile(ApkPackAssets.MaskLayer.Upon),
                                        onPickLauncherIcon = {
                                            apkAssetPickTarget = ApkAssetPickTarget.LauncherIcon
                                            apkAssetPickLauncher.launch(arrayOf("image/*"))
                                        },
                                        onClearLauncherIcon = viewModel::clearApkLauncherIcon,
                                        onPickIconBack = {
                                            apkAssetPickTarget = ApkAssetPickTarget.IconBack
                                            apkAssetPickLauncher.launch(arrayOf("image/*"))
                                        },
                                        onClearIconBack = {
                                            viewModel.clearApkMaskLayer(ApkPackAssets.MaskLayer.Back)
                                        },
                                        onPickIconMask = {
                                            apkAssetPickTarget = ApkAssetPickTarget.IconMask
                                            apkAssetPickLauncher.launch(arrayOf("image/*"))
                                        },
                                        onClearIconMask = {
                                            viewModel.clearApkMaskLayer(ApkPackAssets.MaskLayer.Mask)
                                        },
                                        onPickIconUpon = {
                                            apkAssetPickTarget = ApkAssetPickTarget.IconUpon
                                            apkAssetPickLauncher.launch(arrayOf("image/*"))
                                        },
                                        onClearIconUpon = {
                                            viewModel.clearApkMaskLayer(ApkPackAssets.MaskLayer.Upon)
                                        },
                                        onImportMaskFromPack = {
                                            maskPackImportLauncher.launch(ProjectImportMimeTypes.toTypedArray())
                                        },
                                    )
                                }
                                Screen.Icons -> SecondaryScene(
                                    screen = Screen.Icons,
                                    pageBackground = pageBackground,
                                    scrollBehavior = scrollBehavior,
                                    blurEnabled = viewModel.settings.blurEnabled,
                                    iconPreferences = viewModel.iconPreferences,
                                    onIconPreferences = ::onIconPreferencesChanged,
                                    onBack = ::navigateBack,
                                    onCreateProject = viewModel::createProject,
                                    onImportIcons = {
                                        iconPackImportLauncher.launch(ProjectImportMimeTypes.toTypedArray())
                                    },
                                ) { contentPadding ->
                                    IconEditPage(
                                        items = viewModel.visibleIconItems(),
                                        contentPadding = contentPadding,
                                        loading = viewModel.isProjectLoading,
                                        iconFile = viewModel::iconFile,
                                        onConfirmEdits = { packageName, selectedVariantKey, selectedAdditionIndex, replacements, additions ->
                                            viewModel.commitIconEdits(
                                                packageName = packageName,
                                                selectedVariantKey = selectedVariantKey,
                                                selectedAdditionIndex = selectedAdditionIndex,
                                                replacements = replacements,
                                                additions = additions,
                                            )
                                        },
                                        onDeleteIcon = viewModel::deleteIcon,
                                    )
                                }
                                Screen.ThemeSettings -> SecondaryScene(
                                    screen = Screen.ThemeSettings,
                                    pageBackground = pageBackground,
                                    scrollBehavior = scrollBehavior,
                                    blurEnabled = viewModel.settings.blurEnabled,
                                    iconPreferences = viewModel.iconPreferences,
                                    onIconPreferences = ::onIconPreferencesChanged,
                                    onBack = ::navigateBack,
                                    onCreateProject = viewModel::createProject,
                                ) { contentPadding ->
                                    ThemeSettingsPage(
                                        settings = viewModel.settings,
                                        contentPadding = contentPadding.withPageMargins(horizontal = 0.dp),
                                        onSettings = viewModel::updateSettings,
                                    )
                                }
                                Screen.About -> AboutPage(onBack = ::navigateBack)
                                else -> error("Unknown navigation destination: $targetScreen")
                            }
                        }
                    },
                )

                ConfirmDeleteDialog(
                    project = deleteProject,
                    metadata = deleteProject?.let { viewModel.projectMetadata[it.id] },
                    onDismiss = { deleteProject = null },
                    onConfirm = {
                        viewModel.deleteProject(it.id)
                        deleteProject = null
                    },
                )
                ExportDialog(
                    project = exportProject,
                    validate = viewModel::validateForExport,
                    onDismiss = { exportProject = null },
                    onExport = { project, format ->
                        val extension = when (format) {
                            ExportFormat.Mtz -> "mtz"
                            ExportFormat.ModuleZip -> "zip"
                            ExportFormat.Apk -> "apk"
                        }
                        val suggestedName = viewModel.exportSuggestedName(project, format)
                        val fileName = "$suggestedName.$extension"
                        val mimeType = when (format) {
                            ExportFormat.Mtz -> "application/octet-stream"
                            ExportFormat.ModuleZip -> "application/zip"
                            ExportFormat.Apk -> "application/vnd.android.package-archive"
                        }
                        val target = ExportDirectoryHelper.createExportTarget(
                            context = context,
                            settings = viewModel.settings,
                            mimeType = mimeType,
                            displayName = fileName,
                        )
                        if (target != null) {
                            viewModel.exportProject(
                                id = project.id,
                                format = format,
                                target = target.uri,
                                locationLabel = target.locationLabel,
                            )
                        } else {
                            exportPickerProject = project
                            when (format) {
                                ExportFormat.Mtz -> mtzExportLauncher.launch(fileName)
                                ExportFormat.ModuleZip -> zipExportLauncher.launch(fileName)
                                ExportFormat.Apk -> apkExportLauncher.launch(fileName)
                            }
                        }
                    },
                    onValidationFailed = { project, format ->
                        exportProject = null
                        incompleteExport = project to format
                    },
                )
                ExportValidationDialog(
                    format = incompleteExport?.second,
                    onDismiss = { incompleteExport = null },
                    onComplete = {
                        val (project, format) = incompleteExport ?: return@ExportValidationDialog
                        incompleteExport = null
                        infoTab = when (format) {
                            ExportFormat.Mtz -> InfoTab.Mtz
                            ExportFormat.ModuleZip -> InfoTab.Module
                            ExportFormat.Apk -> InfoTab.Apk
                        }
                        viewModel.loadProject(project.id, loadIcons = false)
                        navigateTo(Screen.Info)
                    },
                )
                MessageDialog(
                    title = viewModel.message?.title,
                    message = viewModel.message?.summary,
                    onDismiss = viewModel::clearMessage,
                )
                IconImportConfirmDialog(
                    preview = viewModel.iconImportPreview,
                    iconFile = viewModel::iconImportCandidateFile,
                    onToggle = viewModel::toggleIconImportSelection,
                    onSelectAll = { viewModel.setAllIconImportSelection(true) },
                    onSelectNone = { viewModel.setAllIconImportSelection(false) },
                    onDismiss = viewModel::dismissIconImportPreview,
                    onOverwrite = { viewModel.applyIconImport(IconImportMode.Overwrite) },
                    onAddOnly = { viewModel.applyIconImport(IconImportMode.AddOnly) },
                )
                MaskLayerImportConfirmDialog(
                    preview = viewModel.maskLayerImportPreview,
                    layerFile = viewModel::maskImportLayerFile,
                    onToggle = viewModel::toggleMaskLayerImportSelection,
                    onSelectAll = { viewModel.setAllMaskLayerImportSelection(true) },
                    onSelectNone = { viewModel.setAllMaskLayerImportSelection(false) },
                    onDismiss = viewModel::dismissMaskLayerImportPreview,
                    onImport = viewModel::applyMaskLayerImport,
                )
                ImportProgressOverlay(progress = viewModel.importProgress)
                ExportProgressOverlay(
                    progress = viewModel.exportProgress,
                    installUri = viewModel.pendingApkInstallUri,
                    onDismiss = {
                        viewModel.clearPendingApkInstall()
                        viewModel.dismissExportProgress()
                    },
                    onInstall = { uri ->
                        if (installExportedApk(context, uri)) {
                            viewModel.clearPendingApkInstall()
                            viewModel.dismissExportProgress()
                        }
                    },
                )
            }
        }
    }
}

private fun Context.requiresInstalledAppsPermission(): Boolean = runCatching {
    // MIUI/HyperOS, ColorOS/OxygenOS and other OEM builds expose this runtime permission.
    // Only requesting it when owned by MIUI caused ColorOS devices to never prompt,
    // leaving getInstalledApplications() with a near-empty result.
    packageManager.getPermissionInfo(InstalledAppsPermission, 0)
    true
}.getOrDefault(false)

private fun installExportedApk(context: Context, uri: Uri): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Toast.makeText(
            context,
            context.getString(R.string.export_apk_install_permission),
            Toast.LENGTH_LONG,
        ).show()
        return false
    }
    val installed = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }.isSuccess
    if (!installed) {
        Toast.makeText(
            context,
            context.getString(R.string.export_apk_install_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
    return installed
}

private fun Intent.projectUri(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data
    Intent.ACTION_SEND -> getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    else -> null
}

@Composable
private fun RootScene(
    rootScreens: List<Screen>,
    useNavigationRail: Boolean,
    pageBackground: Color,
    settings: AppSettings,
    pagerState: PagerState,
    miuixBackdrop: LayerBackdrop?,
    floatingBackdrop: LayerBackdrop,
    iconPreferences: IconPreferences,
    onIconPreferences: (IconPreferences) -> Unit,
    onSettings: (AppSettings) -> Unit,
    onCreateProject: () -> Unit,
    onImportProject: () -> Unit,
    onSelectRoot: (Screen) -> Unit,
    projectsPage: @Composable (PaddingValues) -> Unit,
    settingsPage: @Composable (PaddingValues) -> Unit,
) {
    val hasBottomBar = !useNavigationRail
    val targetRootScreen = rootScreens[pagerState.targetPage.coerceIn(rootScreens.indices)]
    var rootPagerWidthPx by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (hasBottomBar) {
                MainBottomBar(
                    pagerState = pagerState,
                    onSelect = onSelectRoot,
                    settings = settings,
                    backdrop = miuixBackdrop,
                    floatingBackdrop = floatingBackdrop,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.graphicsLayer {
                    val projectPageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    translationX = -projectPageOffset * rootPagerWidthPx
                },
                onClick = onImportProject,
            ) {
                Icon(
                    imageVector = MiuixIcons.Demibold.Add,
                    tint = Color.White,
                    contentDescription = stringResource(R.string.action_import_project),
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = pageBackground,
    ) { padding ->
        val bottomContentPadding = padding.calculateBottomPadding()
        Box(
            Modifier
                .fillMaxSize()
                .background(pageBackground),
        ) {
            Row(Modifier.fillMaxSize()) {
                if (useNavigationRail) {
                    MainNavigationRail(
                        selected = targetRootScreen,
                        settings = settings,
                        backdrop = miuixBackdrop,
                        onSelect = onSelectRoot,
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(
                            if (useNavigationRail || !settings.floatingBottomBar) {
                                miuixBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    RootPagerContent(
                        pagerState = pagerState,
                        pageBackground = pageBackground,
                        floatingBottomBar = settings.floatingBottomBar,
                        floatingBackdrop = floatingBackdrop,
                        onWidthChanged = { rootPagerWidthPx = it },
                        projectsPage = {
                            RootTopBarPage(
                                screen = Screen.Projects,
                                pageBackground = pageBackground,
                                settings = settings,
                                iconPreferences = iconPreferences,
                                onIconPreferences = onIconPreferences,
                                onSettings = onSettings,
                                onCreateProject = onCreateProject,
                                bottomContentPadding = bottomContentPadding,
                                content = projectsPage,
                            )
                        },
                        settingsPage = {
                            RootTopBarPage(
                                screen = Screen.Settings,
                                pageBackground = pageBackground,
                                settings = settings,
                                iconPreferences = iconPreferences,
                                onIconPreferences = onIconPreferences,
                                onSettings = onSettings,
                                onCreateProject = onCreateProject,
                                bottomContentPadding = bottomContentPadding,
                                content = settingsPage,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RootTopBarPage(
    screen: Screen,
    pageBackground: Color,
    settings: AppSettings,
    iconPreferences: IconPreferences,
    onIconPreferences: (IconPreferences) -> Unit,
    onSettings: (AppSettings) -> Unit,
    onCreateProject: () -> Unit,
    bottomContentPadding: Dp,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberMiuixBlurBackdrop(settings.blurEnabled)
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                screen = screen,
                backdrop = topBarBackdrop,
                blurEnabled = settings.blurEnabled,
                scrollBehavior = scrollBehavior,
                onBack = {},
                onCreateProject = onCreateProject,
                iconPreferences = iconPreferences,
                onIconPreferences = onIconPreferences,
                projectSortField = settings.projectSortField,
                onProjectSortField = { onSettings(settings.copy(projectSortField = it)) },
            )
        },
        containerColor = pageBackground,
    ) { padding ->
        val contentPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(layoutDirection),
            bottom = maxOf(padding.calculateBottomPadding(), bottomContentPadding),
        )
        Box(
            Modifier
                .fillMaxSize()
                .then(topBarBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .background(pageBackground)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            content(contentPadding)
        }
    }
}

@Composable
private fun SecondaryScene(
    screen: Screen,
    pageBackground: Color,
    scrollBehavior: ScrollBehavior,
    blurEnabled: Boolean,
    iconPreferences: IconPreferences,
    onIconPreferences: (IconPreferences) -> Unit,
    onBack: () -> Unit,
    onCreateProject: () -> Unit,
    infoTab: InfoTab = InfoTab.Mtz,
    onInfoTab: (InfoTab) -> Unit = {},
    onImportIcons: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val topBarBackdrop = rememberMiuixBlurBackdrop(blurEnabled)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                screen = screen,
                backdrop = topBarBackdrop,
                blurEnabled = blurEnabled,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                onCreateProject = onCreateProject,
                iconPreferences = iconPreferences,
                onIconPreferences = onIconPreferences,
                infoTab = infoTab,
                onInfoTab = onInfoTab,
                onImportIcons = onImportIcons,
            )
        },
        containerColor = pageBackground,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .then(topBarBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .background(pageBackground),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                content(padding)
            }
        }
    }
}
