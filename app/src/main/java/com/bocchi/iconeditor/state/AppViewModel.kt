package com.bocchi.iconeditor.state

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.data.ApkPackAssets
import com.bocchi.iconeditor.data.IconMappingBridge
import com.bocchi.iconeditor.data.InvalidIconPackApkException
import com.bocchi.iconeditor.data.InvalidProjectArchiveException
import com.bocchi.iconeditor.data.NoMaskLayersFoundException
import com.bocchi.iconeditor.data.ProjectRepository
import com.bocchi.iconeditor.data.packagename.PackageNameRepository
import com.bocchi.iconeditor.data.sync.ProjectSyncAction
import com.bocchi.iconeditor.data.sync.ProjectSyncClient
import com.bocchi.iconeditor.data.sync.ProjectSyncConnectionParser
import com.bocchi.iconeditor.data.sync.ProjectSyncConstants
import com.bocchi.iconeditor.data.sync.ProjectSyncDiffer
import com.bocchi.iconeditor.data.sync.ProjectSyncDiffPreview
import com.bocchi.iconeditor.data.sync.ProjectSyncHttpServer
import com.bocchi.iconeditor.data.sync.ProjectSyncKind
import com.bocchi.iconeditor.data.sync.ProjectSyncOrchestrator
import com.bocchi.iconeditor.data.sync.ProjectSyncPeerAnnounce
import com.bocchi.iconeditor.data.sync.ProjectSyncRouteHandler
import com.bocchi.iconeditor.model.ApkInfo
import com.bocchi.iconeditor.model.AppSettings
import com.bocchi.iconeditor.model.ExportFormat
import com.bocchi.iconeditor.model.ExportPhase
import com.bocchi.iconeditor.model.ExportProgress
import com.bocchi.iconeditor.model.IconAsset
import com.bocchi.iconeditor.model.IconImportCandidate
import com.bocchi.iconeditor.model.IconImportMode
import com.bocchi.iconeditor.model.IconImportPreview
import com.bocchi.iconeditor.model.MaskLayerImportCandidate
import com.bocchi.iconeditor.model.MaskLayerImportPreview
import com.bocchi.iconeditor.model.IconListItem
import com.bocchi.iconeditor.model.IconPreferences
import com.bocchi.iconeditor.model.ImportProgress
import com.bocchi.iconeditor.model.LocalAppInfo
import com.bocchi.iconeditor.model.ModuleInfo
import com.bocchi.iconeditor.model.MtzInfo
import com.bocchi.iconeditor.model.ProjectMetadata
import com.bocchi.iconeditor.model.ProjectSummary
import com.bocchi.iconeditor.model.SortField
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppMessage(
    val title: String,
    val summary: String,
    val canUndo: Boolean = false,
)

data class IconUndoOffer(
    val token: String,
    val label: String,
    val alsoRemovePackages: List<String> = emptyList(),
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProjectRepository(application)
    private val packageNameRepository = PackageNameRepository(application)
    private var localAppsCache: List<LocalAppInfo>? = null
    private var localAppsPreloadJob: Job? = null
    private var projectLoadJob: Job? = null

    var projects by mutableStateOf<List<ProjectSummary>>(emptyList())
        private set
    var projectMetadata by mutableStateOf<Map<String, ProjectMetadata>>(emptyMap())
        private set
    var settings by mutableStateOf(repository.loadSettings())
        private set
    var selectedProjectId by mutableStateOf<String?>(null)
        private set
    var metadata by mutableStateOf(ProjectMetadata())
        private set
    var iconPreferences by mutableStateOf(IconPreferences())
        private set
    var icons by mutableStateOf<List<IconAsset>>(emptyList())
        private set
    var localApps by mutableStateOf<List<LocalAppInfo>>(emptyList())
        private set
    var message by mutableStateOf<AppMessage?>(null)
        private set
    var iconUndoOffer by mutableStateOf<IconUndoOffer?>(null)
        private set
    var trashEntries by mutableStateOf<List<com.bocchi.iconeditor.model.TrashEntry>>(emptyList())
        private set
    var isProjectLoading by mutableStateOf(false)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var importProgress by mutableStateOf<ImportProgress?>(null)
        private set
    var iconImportPreview by mutableStateOf<IconImportPreview?>(null)
        private set
    var maskLayerImportPreview by mutableStateOf<MaskLayerImportPreview?>(null)
        private set
    var isExporting by mutableStateOf(false)
        private set
    var exportProgress by mutableStateOf<ExportProgress?>(null)
        private set
    var pendingApkInstallUri by mutableStateOf<Uri?>(null)
        private set
    var lastExportUri by mutableStateOf<Uri?>(null)
        private set
    var projectsScrollToTopRequest by mutableIntStateOf(0)
        private set
    var apkAssetsRevision by mutableIntStateOf(0)
        private set

    // Project LAN sync
    var syncServerRunning by mutableStateOf(false)
        private set
    var syncServerPort by mutableIntStateOf(ProjectSyncConstants.DEFAULT_PORT)
        private set
    var syncServerToken by mutableStateOf("")
        private set
    var syncLanAddress by mutableStateOf<String?>(null)
        private set
    var syncPeerHost by mutableStateOf(settings.syncPeerHost)
    var syncPeerPort by mutableStateOf(
        settings.syncPeerPort.ifBlank { ProjectSyncConstants.DEFAULT_PORT.toString() },
    )
    var syncPeerToken by mutableStateOf(settings.syncPeerToken)
    var syncStatusMessage by mutableStateOf<String?>(null)
        private set
    var syncBusy by mutableStateOf(false)
        private set
    var syncApplyDone by mutableIntStateOf(0)
        private set
    var syncApplyTotal by mutableIntStateOf(0)
        private set
    val syncApplyFraction: Float
        get() = if (syncApplyTotal <= 0) 0f else (syncApplyDone.toFloat() / syncApplyTotal).coerceIn(0f, 1f)
    var syncDiffPreview by mutableStateOf<ProjectSyncDiffPreview?>(null)
        private set
    private var syncHttpServer: ProjectSyncHttpServer? = null

    private val initializationJob: Job

    init {
        initializationJob = initialize()
    }

    private fun initialize(): Job = viewModelScope.launch {
        try {
            val initialData = withContext(Dispatchers.IO) {
                val loadedProjects = repository.loadProjects()
                InitialData(
                    projects = loadedProjects,
                    projectMetadata = loadedProjects.associate { it.id to repository.loadMetadata(it.id) },
                )
            }
            projects = initialData.projects
            projectMetadata = initialData.projectMetadata
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            showError(error)
        }
    }

    fun refresh() {
        projects = repository.loadProjects()
        projectMetadata = projects.associate { it.id to repository.loadMetadata(it.id) }
        settings = repository.loadSettings()
        trashEntries = repository.loadTrash()
        selectedProjectId?.let { loadProject(it, loadIcons = false) }
    }

    fun clearMessage() {
        message = null
    }

    fun dismissIconUndo() {
        iconUndoOffer = null
        repository.clearIconUndoSnapshots()
    }

    fun undoLastIconChange() = runAction {
        val offer = iconUndoOffer ?: return@runAction
        repository.restoreIconUndoSnapshot(offer.token, offer.alsoRemovePackages)
        iconUndoOffer = null
        message = null
        selectedProjectId?.let {
            iconPreferences = repository.loadIconPreferences(it)
            icons = repository.loadIcons(it)
        }
        refresh()
        showMessage(R.string.dialog_notice, getApplication<Application>().getString(R.string.undo_done))
    }

    fun refreshTrash() {
        trashEntries = repository.loadTrash()
    }

    fun restoreTrashProject(id: String) = runAction {
        repository.restoreProjectFromTrash(id)
        refresh()
        showMessage(R.string.dialog_notice, getApplication<Application>().getString(R.string.trash_restored))
    }

    fun purgeTrashProject(id: String) = runAction {
        repository.purgeTrashProject(id)
        refreshTrash()
    }

    fun emptyTrash() = runAction {
        repository.emptyTrash()
        refreshTrash()
    }

    override fun onCleared() {
        stopSyncServer()
        super.onCleared()
    }

    fun notifyInstalledAppsPermissionDenied() {
        showMessage(
            R.string.dialog_notice,
            getApplication<Application>().getString(R.string.installed_apps_permission_denied),
        )
    }

    fun preloadInstalledApps(forceRefresh: Boolean = false) {
        if (!forceRefresh && (localAppsCache != null || localAppsPreloadJob?.isActive == true)) return
        localAppsPreloadJob?.cancel()
        localAppsPreloadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                if (forceRefresh) invalidateLocalAppsCache()
                loadLocalAppsSnapshot()
            }
            localApps = loaded
        }
    }

    fun createProject() = runAction {
        val project = repository.createProject()
        refresh()
        loadProject(project.id, loadIcons = false)
        projectsScrollToTopRequest++
    }

    fun importProject(uri: Uri, displayName: String) {
        viewModelScope.launch {
            initializationJob.join()
            isImporting = true
            importProgress = ImportProgress(com.bocchi.iconeditor.model.ImportPhase.Copying)
            val progressChannel = Channel<ImportProgress>(Channel.CONFLATED)
            val progressCollector = launch(Dispatchers.Main.immediate) {
                for (progress in progressChannel) {
                    importProgress = progress
                }
            }
            runCatching {
                val project = withContext(Dispatchers.IO) {
                    repository.importProject(uri, displayName) { progress ->
                        progressChannel.trySend(progress)
                    }
                }
                refresh()
                loadProject(project.id, loadIcons = false)
                projectsScrollToTopRequest++
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (error is InvalidProjectArchiveException || error is InvalidIconPackApkException) {
                    showMessage(R.string.import_failed_title, getApplication<Application>().getString(R.string.import_invalid_file))
                } else {
                    showError(error)
                }
            }
            progressChannel.close()
            progressCollector.join()
            isImporting = false
            importProgress = null
        }
    }

    fun previewIconsFromPack(uri: Uri) {
        val projectId = selectedProjectId ?: return
        viewModelScope.launch {
            initializationJob.join()
            iconImportPreview?.let { discard ->
                withContext(Dispatchers.IO) {
                    repository.discardIconImportStaging(discard.stagingId)
                }
            }
            iconImportPreview = null
            isImporting = true
            importProgress = ImportProgress(com.bocchi.iconeditor.model.ImportPhase.Copying)
            val progressChannel = Channel<ImportProgress>(Channel.CONFLATED)
            val progressCollector = launch(Dispatchers.Main.immediate) {
                for (progress in progressChannel) {
                    importProgress = progress
                }
            }
            runCatching {
                val preview = withContext(Dispatchers.IO) {
                    packageNameRepository.ensureLoaded()
                    val staged = repository.previewIconsFromPack(projectId, uri) { progress ->
                        progressChannel.trySend(progress)
                    }
                    val localByPackage = localApps.associateBy { it.packageName }
                    staged.copy(
                        items = staged.items.map { item ->
                            val resolved = packageNameRepository.resolveAppName(
                                packageName = item.packageName,
                                localAppName = localByPackage[item.packageName]?.appName
                                    ?: item.appName.takeIf { it != item.packageName },
                            )
                            item.copy(appName = resolved)
                        }.sortedWith(
                            compareByDescending<IconImportCandidate> { it.conflict }
                                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName },
                        ),
                    )
                }
                iconImportPreview = preview
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (error is InvalidProjectArchiveException || error is InvalidIconPackApkException) {
                    showMessage(
                        R.string.import_failed_title,
                        getApplication<Application>().getString(R.string.import_invalid_file),
                    )
                } else {
                    showError(error)
                }
            }
            progressChannel.close()
            progressCollector.join()
            isImporting = false
            importProgress = null
        }
    }

    fun dismissIconImportPreview() {
        val preview = iconImportPreview
        iconImportPreview = null
        if (preview != null) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.discardIconImportStaging(preview.stagingId)
            }
        }
    }

    fun toggleIconImportSelection(packageName: String) {
        val preview = iconImportPreview ?: return
        iconImportPreview = preview.copy(
            items = preview.items.map { item ->
                if (item.packageName == packageName) item.copy(selected = !item.selected) else item
            },
        )
    }

    fun setAllIconImportSelection(selected: Boolean) {
        val preview = iconImportPreview ?: return
        iconImportPreview = preview.copy(
            items = preview.items.map { it.copy(selected = selected) },
        )
    }

    fun iconImportCandidateFile(candidate: IconImportCandidate): File? {
        val preview = iconImportPreview ?: return null
        return repository.iconImportCandidateFile(preview.stagingId, candidate.iconArchivePath)
            .takeIf { it.isFile }
    }

    fun previewMaskLayersFromPack(uri: Uri) {
        val projectId = selectedProjectId ?: return
        viewModelScope.launch {
            initializationJob.join()
            maskLayerImportPreview?.let { discard ->
                withContext(Dispatchers.IO) {
                    repository.discardMaskImportStaging(discard.stagingId)
                }
            }
            maskLayerImportPreview = null
            isImporting = true
            importProgress = ImportProgress(com.bocchi.iconeditor.model.ImportPhase.Copying)
            val progressChannel = Channel<ImportProgress>(Channel.CONFLATED)
            val progressCollector = launch(Dispatchers.Main.immediate) {
                for (progress in progressChannel) {
                    importProgress = progress
                }
            }
            runCatching {
                val preview = withContext(Dispatchers.IO) {
                    repository.previewMaskLayersFromPack(projectId, uri) { progress ->
                        progressChannel.trySend(progress)
                    }
                }
                maskLayerImportPreview = preview
            }.onFailure { error ->
                if (error is CancellationException) throw error
                when (error) {
                    is NoMaskLayersFoundException -> showMessage(
                        R.string.mask_import_failed_title,
                        getApplication<Application>().getString(R.string.mask_import_none_found),
                    )
                    is InvalidProjectArchiveException, is InvalidIconPackApkException -> showMessage(
                        R.string.import_failed_title,
                        getApplication<Application>().getString(R.string.import_invalid_file),
                    )
                    else -> showError(error)
                }
            }
            progressChannel.close()
            progressCollector.join()
            isImporting = false
            importProgress = null
        }
    }

    fun dismissMaskLayerImportPreview() {
        val preview = maskLayerImportPreview
        maskLayerImportPreview = null
        if (preview != null) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.discardMaskImportStaging(preview.stagingId)
            }
        }
    }

    fun toggleMaskLayerImportSelection(layerName: String) {
        val preview = maskLayerImportPreview ?: return
        maskLayerImportPreview = preview.copy(
            items = preview.items.map { item ->
                if (item.layerName == layerName && item.found) {
                    item.copy(selected = !item.selected)
                } else {
                    item
                }
            },
        )
    }

    fun setAllMaskLayerImportSelection(selected: Boolean) {
        val preview = maskLayerImportPreview ?: return
        maskLayerImportPreview = preview.copy(
            items = preview.items.map { item ->
                if (item.found) item.copy(selected = selected) else item
            },
        )
    }

    fun applyMaskLayerImport() {
        val projectId = selectedProjectId ?: return
        val preview = maskLayerImportPreview ?: return
        val selected = preview.items.filter { it.selected && it.found }.map { it.layerName }.toSet()
        if (selected.isEmpty()) {
            showMessage(
                R.string.dialog_notice,
                getApplication<Application>().getString(R.string.mask_import_nothing_selected),
            )
            return
        }
        maskLayerImportPreview = null
        viewModelScope.launch {
            isImporting = true
            importProgress = ImportProgress(com.bocchi.iconeditor.model.ImportPhase.Copying)
            val progressChannel = Channel<ImportProgress>(Channel.CONFLATED)
            val progressCollector = launch(Dispatchers.Main.immediate) {
                for (progress in progressChannel) {
                    importProgress = progress
                }
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.applyMaskLayerImport(
                        projectId = projectId,
                        preview = preview,
                        selectedLayers = selected,
                    ) { progress ->
                        progressChannel.trySend(progress)
                    }
                }
                apkAssetsRevision++
                refresh()
                showMessage(
                    R.string.mask_import_success_title,
                    getApplication<Application>().getString(
                        R.string.mask_import_success_summary,
                        selected.size,
                    ),
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                repository.discardMaskImportStaging(preview.stagingId)
                showError(error)
            }
            progressChannel.close()
            progressCollector.join()
            isImporting = false
            importProgress = null
        }
    }

    fun maskImportLayerFile(item: MaskLayerImportCandidate): File? {
        val preview = maskLayerImportPreview ?: return null
        if (!item.found) return null
        return repository.maskImportLayerFile(preview.stagingId, item.layerName)
    }

    fun applyIconImport(mode: IconImportMode) {
        val projectId = selectedProjectId ?: return
        val preview = iconImportPreview ?: return
        val selectedPackages = preview.items.filter { it.selected }.map { it.packageName }.toSet()
        if (selectedPackages.isEmpty()) {
            showMessage(
                R.string.dialog_notice,
                getApplication<Application>().getString(R.string.icon_import_nothing_selected),
            )
            return
        }
        iconImportPreview = null
        viewModelScope.launch {
            isImporting = true
            importProgress = ImportProgress(com.bocchi.iconeditor.model.ImportPhase.Copying)
            val progressChannel = Channel<ImportProgress>(Channel.CONFLATED)
            val progressCollector = launch(Dispatchers.Main.immediate) {
                for (progress in progressChannel) {
                    importProgress = progress
                }
            }
            runCatching {
                val appliedCount = withContext(Dispatchers.IO) {
                    repository.applyIconImport(
                        projectId = projectId,
                        preview = preview,
                        mode = mode,
                        selectedPackages = selectedPackages,
                    ) { progress ->
                        progressChannel.trySend(progress)
                    }
                    when (mode) {
                        IconImportMode.Overwrite -> selectedPackages.size
                        IconImportMode.AddOnly -> preview.items.count { it.selected && !it.conflict }
                    }
                }
                loadProject(projectId, loadIcons = true)
                refresh()
                val message = when (mode) {
                    IconImportMode.Overwrite -> getApplication<Application>().getString(
                        R.string.icon_import_done_overwrite,
                        appliedCount,
                    )
                    IconImportMode.AddOnly -> getApplication<Application>().getString(
                        R.string.icon_import_done_add_only,
                        appliedCount,
                    )
                }
                showMessage(R.string.icon_import_done_title, message)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                repository.discardIconImportStaging(preview.stagingId)
                showError(error)
            }
            progressChannel.close()
            progressCollector.join()
            isImporting = false
            importProgress = null
        }
    }

    fun deleteProject(id: String) = runAction {
        repository.deleteProject(id)
        if (selectedProjectId == id) selectedProjectId = null
        refresh()
        showMessage(R.string.dialog_notice, getApplication<Application>().getString(R.string.trash_moved))
    }

    fun renameProject(id: String, name: String) = runAction {
        repository.renameProject(id, name)
        refresh()
    }

    fun loadProject(id: String, loadIcons: Boolean) {
        projectLoadJob?.cancel()
        selectedProjectId = id
        metadata = projectMetadata[id] ?: ProjectMetadata()
        if (loadIcons) {
            icons = emptyList()
            isProjectLoading = true
        } else {
            isProjectLoading = false
        }
        projectLoadJob = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val loadedPreferences = repository.loadIconPreferences(id)
                    if (loadIcons) packageNameRepository.ensureLoaded()
                    LoadedProject(
                        metadata = repository.loadMetadata(id),
                        preferences = loadedPreferences,
                        icons = if (loadIcons) repository.loadIcons(id) else emptyList(),
                        localApps = if (loadIcons) loadLocalAppsSnapshot() else emptyList(),
                    )
                }
                if (selectedProjectId != id) return@launch
                metadata = loaded.metadata
                iconPreferences = loaded.preferences
                apkAssetsRevision++
                if (loadIcons) {
                    icons = loaded.icons
                    localApps = loaded.localApps
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (selectedProjectId == id) {
                    showError(error)
                }
            } finally {
                if (selectedProjectId == id && loadIcons) isProjectLoading = false
            }
        }
    }

    fun saveMtzInfo(info: MtzInfo) = runAction {
        selectedProjectId?.let {
            metadata = metadata.copy(mtz = info)
            repository.saveMetadata(it, metadata)
            refresh()
        }
    }

    fun saveModuleInfo(info: ModuleInfo) = runAction {
        selectedProjectId?.let {
            metadata = metadata.copy(module = info)
            repository.saveMetadata(it, metadata)
            refresh()
        }
    }

    fun saveApkInfo(info: ApkInfo) = runAction {
        selectedProjectId?.let {
            metadata = metadata.copy(apk = info)
            repository.saveMetadata(it, metadata)
            refresh()
        }
    }

    fun apkLauncherIconFile(): File? {
        // Read revision so Compose observers recompose after replace/clear.
        apkAssetsRevision
        return selectedProjectId?.let { repository.apkLauncherIconFile(it) }
    }

    fun apkMaskLayerFile(layer: ApkPackAssets.MaskLayer): File? {
        apkAssetsRevision
        return selectedProjectId?.let { repository.apkMaskLayerFile(it, layer) }
    }

    fun setApkLauncherIcon(uri: Uri) = runAction {
        selectedProjectId?.let {
            repository.setApkLauncherIcon(it, uri)
            apkAssetsRevision++
            refresh()
        }
    }

    fun setApkMaskLayer(layer: ApkPackAssets.MaskLayer, uri: Uri) = runAction {
        selectedProjectId?.let {
            repository.setApkMaskLayer(it, layer, uri)
            apkAssetsRevision++
            refresh()
        }
    }

    fun clearApkLauncherIcon() = runAction {
        selectedProjectId?.let {
            repository.clearApkLauncherIcon(it)
            apkAssetsRevision++
            refresh()
        }
    }

    fun clearApkMaskLayer(layer: ApkPackAssets.MaskLayer) = runAction {
        selectedProjectId?.let {
            repository.clearApkMaskLayer(it, layer)
            apkAssetsRevision++
            refresh()
        }
    }

    fun updateIconPreferences(preferences: IconPreferences) = runAction {
        val enabledInstalledAppSource =
            (!iconPreferences.showLocalApps && preferences.showLocalApps) ||
                (!iconPreferences.showSystemApps && preferences.showSystemApps)
        iconPreferences = preferences
        selectedProjectId?.let { repository.saveIconPreferences(it, preferences) }
        if (enabledInstalledAppSource) {
            preloadInstalledApps(forceRefresh = true)
        }
    }

    fun commitIconEdits(
        isNew: Boolean,
        originalPackageName: String,
        packageName: String,
        appName: String,
        aliasPackageNames: List<String>,
        selectedVariantKey: String?,
        selectedAdditionIndex: Int?,
        replacements: List<Pair<IconAsset, Uri>>,
        additions: List<Uri>,
    ): Boolean {
        return try {
            val projectId = selectedProjectId ?: return false
            val trimmedPackage = packageName.trim()
            val trimmedAppName = appName.trim()
            val original = originalPackageName.trim()
            val aliases = IconMappingBridge.normalizeAliasPackageNames(aliasPackageNames, trimmedPackage)

            if (!IconMappingBridge.isValidAndroidPackageName(trimmedPackage)) {
                error(getApplication<Application>().getString(R.string.icon_edit_invalid_package, trimmedPackage))
            }
            if (trimmedAppName.isEmpty()) {
                error(getApplication<Application>().getString(R.string.icon_edit_empty_app_name))
            }
            for (alias in aliases) {
                if (!IconMappingBridge.isValidAndroidPackageName(alias)) {
                    error(getApplication<Application>().getString(R.string.icon_edit_invalid_alias, alias))
                }
            }

            val existingPackages = icons.map { it.packageName }.toSet()
            val mapping = repository.loadIconMapping(projectId)
            for (alias in aliases) {
                if (existingPackages.contains(alias) && alias != original) {
                    error(getApplication<Application>().getString(R.string.icon_edit_alias_has_icon, alias))
                }
                val ownedByOther = mapping.entries.firstOrNull { entry ->
                    entry.packageName != trimmedPackage &&
                        entry.packageName != original &&
                        entry.aliasPackageNames.contains(alias)
                }
                if (ownedByOther != null) {
                    error(
                        getApplication<Application>().getString(
                            R.string.icon_edit_alias_owned,
                            alias,
                            ownedByOther.packageName,
                        ),
                    )
                }
                if (
                    mapping.entries.any {
                        it.packageName == alias && it.packageName != trimmedPackage && it.packageName != original
                    }
                ) {
                    error(getApplication<Application>().getString(R.string.icon_edit_alias_is_primary, alias))
                }
            }

            if (isNew) {
                if (existingPackages.contains(trimmedPackage)) {
                    error(getApplication<Application>().getString(R.string.icon_edit_package_exists, trimmedPackage))
                }
                if (additions.isEmpty()) {
                    error(getApplication<Application>().getString(R.string.icon_edit_need_image))
                }
            } else if (original.isNotEmpty() && original != trimmedPackage) {
                if (existingPackages.contains(trimmedPackage)) {
                    error(getApplication<Application>().getString(R.string.icon_edit_package_exists, trimmedPackage))
                }
            }

            val snapshotPackage = if (isNew) trimmedPackage else original.ifBlank { trimmedPackage }
            val undoToken = repository.beginIconUndoSnapshot(
                projectId,
                snapshotPackage,
                trimmedAppName.ifBlank { snapshotPackage },
            )

            if (!isNew && original.isNotEmpty() && original != trimmedPackage) {
                replacements.forEach { (asset, uri) ->
                    repository.replaceIcon(projectId, asset, uri)
                }
                repository.renameIconPackage(projectId, original, trimmedPackage)
                val prefs = iconPreferences
                val migratedSelected = prefs.selectedVariants[original]?.let { selected ->
                    when {
                        selected == original -> trimmedPackage
                        selected.startsWith("${original}_") ->
                            trimmedPackage + selected.removePrefix(original)
                        else -> trimmedPackage
                    }
                }
                var nextVariants = prefs.selectedVariants - original
                if (migratedSelected != null) {
                    nextVariants = nextVariants + (trimmedPackage to migratedSelected)
                }
                var nextCustoms = prefs.customAppNames - original
                prefs.customAppNames[original]?.let { custom ->
                    nextCustoms = nextCustoms + (trimmedPackage to custom)
                }
                iconPreferences = prefs.copy(
                    selectedVariants = nextVariants,
                    customAppNames = nextCustoms,
                )
            } else {
                replacements.forEach { (asset, uri) ->
                    repository.replaceIcon(projectId, asset, uri)
                }
            }

            var workingSelectedKey = if (isNew || original == trimmedPackage) {
                selectedVariantKey
            } else {
                selectedVariantKey?.let { selected ->
                    when {
                        selected == original -> trimmedPackage
                        selected.startsWith("${original}_") ->
                            trimmedPackage + selected.removePrefix(original)
                        else -> trimmedPackage
                    }
                }
            }

            val addedVariantKeys = additions.map { uri ->
                repository.addIconVariant(projectId, trimmedPackage, uri)
            }
            workingSelectedKey = selectedAdditionIndex
                ?.let(addedVariantKeys::getOrNull)
                ?: if (isNew) addedVariantKeys.firstOrNull() else workingSelectedKey

            val normalizedSelectedKey = repository.normalizeIconVariants(
                id = projectId,
                packageName = trimmedPackage,
                selectedVariantKey = workingSelectedKey,
            )

            val catalogName = packageNameRepository.resolveAppName(trimmedPackage, null)
            var nextCustoms = iconPreferences.customAppNames.toMutableMap()
            if (trimmedAppName != trimmedPackage && trimmedAppName != catalogName) {
                nextCustoms[trimmedPackage] = trimmedAppName
            } else {
                nextCustoms.remove(trimmedPackage)
            }
            var nextVariants = iconPreferences.selectedVariants.toMutableMap()
            if (normalizedSelectedKey != null) {
                nextVariants[trimmedPackage] = normalizedSelectedKey
            } else {
                nextVariants.remove(trimmedPackage)
            }
            iconPreferences = iconPreferences.copy(
                selectedVariants = nextVariants,
                customAppNames = nextCustoms,
            )
            repository.saveIconPreferences(projectId, iconPreferences)
            repository.updateIconAliasPackageNames(projectId, trimmedPackage, aliases)
            icons = repository.loadIcons(projectId)
            refresh()
            val alsoRemove = if (!isNew && original.isNotEmpty() && original != trimmedPackage) {
                listOf(trimmedPackage)
            } else if (isNew) {
                listOf(trimmedPackage)
            } else {
                emptyList()
            }
            iconUndoOffer = IconUndoOffer(
                token = undoToken,
                label = trimmedAppName,
                alsoRemovePackages = alsoRemove,
            )
            message = AppMessage(
                title = getApplication<Application>().getString(R.string.dialog_notice),
                summary = if (isNew) {
                    getApplication<Application>().getString(R.string.icon_edit_added, trimmedAppName)
                } else {
                    getApplication<Application>().getString(R.string.icon_edit_updated, trimmedAppName)
                },
                canUndo = true,
            )
            true
        } catch (error: Throwable) {
            showError(error)
            false
        }
    }

    fun resetIcon(asset: IconAsset) = runAction {
        selectedProjectId?.let {
            repository.resetIcon(it, asset)
            icons = repository.loadIcons(it)
            refresh()
        }
    }

    fun deleteIcon(asset: IconAsset) = runAction {
        selectedProjectId?.let {
            val undoToken = repository.beginIconUndoSnapshot(
                it,
                asset.packageName,
                asset.packageName,
            )
            repository.deleteIcon(it, asset)
            if (iconPreferences.selectedVariants[asset.packageName] == asset.variantKey) {
                iconPreferences = iconPreferences.copy(selectedVariants = iconPreferences.selectedVariants - asset.packageName)
                repository.saveIconPreferences(it, iconPreferences)
            }
            icons = repository.loadIcons(it)
            refresh()
            iconUndoOffer = IconUndoOffer(token = undoToken, label = asset.packageName)
            message = AppMessage(
                title = getApplication<Application>().getString(R.string.dialog_notice),
                summary = getApplication<Application>().getString(R.string.icon_deleted_undoable),
                canUndo = true,
            )
        }
    }

    fun dismissExportProgress() {
        exportProgress = null
    }

    fun clearPendingApkInstall() {
        pendingApkInstallUri = null
    }

    fun clearLastExportUri() {
        lastExportUri = null
    }

    fun exportProject(id: String, format: ExportFormat, target: Uri, locationLabel: String = "") {
        viewModelScope.launch {
            isExporting = true
            pendingApkInstallUri = null
            lastExportUri = target
            exportProgress = ExportProgress(com.bocchi.iconeditor.model.ExportPhase.Preparing)
            val progressChannel = Channel<ExportProgress>(capacity = Channel.UNLIMITED)
            var lastProgress: ExportProgress? = null
            val progressCollector = launch(Dispatchers.Main.immediate) {
                for (progress in progressChannel) {
                    lastProgress = progress
                    exportProgress = progress
                }
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.exportProject(id, format, target) { progress ->
                        progressChannel.trySend(progress)
                    }
                }
                refresh()
                val message = if (locationLabel.isNotBlank()) {
                    getApplication<Application>().getString(R.string.export_complete_path, locationLabel)
                } else {
                    getApplication<Application>().getString(R.string.export_complete)
                }
                exportProgress = (lastProgress ?: ExportProgress(ExportPhase.Finishing)).copy(
                    phase = ExportPhase.Finishing,
                    finished = true,
                    success = true,
                    detail = message,
                    logs = (lastProgress?.logs ?: emptyList()) + message,
                )
                if (format == ExportFormat.Apk) {
                    val installUri = withContext(Dispatchers.IO) {
                        runCatching { repository.prepareInstallableApkUri(target) }
                    }
                    pendingApkInstallUri = installUri.getOrElse { error ->
                        showError(error)
                        target
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                val summary = error.message?.takeIf { it.isNotBlank() }
                    ?: getApplication<Application>().getString(R.string.export_failed_generic)
                exportProgress = (lastProgress ?: ExportProgress(ExportPhase.Finishing)).copy(
                    phase = ExportPhase.Finishing,
                    finished = true,
                    success = false,
                    detail = summary,
                    logs = (lastProgress?.logs ?: emptyList()) + summary,
                )
                showError(error)
            }
            progressChannel.close()
            progressCollector.join()
            isExporting = false
        }
    }

    fun validateForExport(id: String, format: ExportFormat): List<String> {
        return repository.validateForExport(id, format)
    }

    fun exportSuggestedName(project: ProjectSummary, format: ExportFormat): String {
        val projectInfo = projectMetadata[project.id]
        return when (format) {
            ExportFormat.Mtz -> projectInfo?.mtz?.title
            ExportFormat.ModuleZip -> projectInfo?.module?.name
            ExportFormat.Apk -> projectInfo?.apk?.label
        }.orEmpty().trim().ifBlank { project.name }
    }

    fun updateSettings(settings: AppSettings) = runAction {
        this.settings = settings
        syncPeerHost = settings.syncPeerHost
        syncPeerPort = settings.syncPeerPort.ifBlank { ProjectSyncConstants.DEFAULT_PORT.toString() }
        syncPeerToken = settings.syncPeerToken
        repository.saveSettings(settings)
    }

    fun persistSyncPeerSettings() {
        updateSettings(
            settings.copy(
                syncPeerHost = syncPeerHost.trim(),
                syncPeerPort = syncPeerPort.trim().ifBlank { ProjectSyncConstants.DEFAULT_PORT.toString() },
                syncPeerToken = syncPeerToken.trim(),
            ),
        )
        if (hasSyncPeerConfigured()) {
            ensureLocalServerAndRegisterWithPeer()
        }
    }

    fun hasSyncPeerConfigured(): Boolean =
        syncPeerHost.isNotBlank() && syncPeerToken.isNotBlank()

    fun syncProject(projectId: String) {
        if (!hasSyncPeerConfigured()) return
        loadProject(projectId, loadIcons = false)
        buildSyncDiffFromPeer()
    }

    fun iconFile(asset: IconAsset) = selectedProjectId?.let { repository.iconFile(it, asset) }

    fun visibleIconItems(): List<IconListItem> {
        val localByPackage = localApps.associateBy { it.packageName }
        val grouped = icons.groupBy { it.packageName }
        val mappingByPackage = selectedProjectId
            ?.let { repository.loadIconMapping(it) }
            ?.entries
            ?.associateBy { it.packageName }
            .orEmpty()
        val coveredByAlias = mappingByPackage.values
            .flatMap { it.aliasPackageNames }
            .toSet()
        val normalLocalPackages = localApps
            .filter { !it.system }
            .map { it.packageName }
        val systemPackages = localApps
            .filter { it.system }
            .map { it.packageName }
        val packages = grouped.keys +
            (if (iconPreferences.showLocalApps || iconPreferences.onlyShowUnadaptedIcons) {
                normalLocalPackages
            } else {
                emptyList()
            }) +
            (if (iconPreferences.showSystemApps) systemPackages else emptyList())

        val query = iconPreferences.search.trim().lowercase()
        val items = packages.distinct().map { packageName ->
            val variants = grouped[packageName].orEmpty().sortedWith(
                compareBy<IconAsset> { variantOrder(it.variantKey, packageName) }
                    .thenBy { it.variantKey },
            )
            val selectedKey = iconPreferences.selectedVariants[packageName]
            val selected = variants.firstOrNull { it.variantKey == selectedKey } ?: variants.firstOrNull()
            val local = localByPackage[packageName]
            val customName = iconPreferences.customAppNames[packageName]
            val aliases = mappingByPackage[packageName]?.aliasPackageNames.orEmpty()
            IconListItem(
                packageName = packageName,
                appName = packageNameRepository.resolveAppName(
                    packageName,
                    customName ?: local?.appName,
                ),
                variants = variants,
                selected = selected,
                localApp = local,
                adapted = variants.isNotEmpty() || packageName in coveredByAlias,
                aliasPackageNames = aliases,
            )
        }.filter { item ->
            val matchesSearch = query.isBlank() ||
                item.packageName.lowercase().contains(query) ||
                item.appName.lowercase().contains(query) ||
                item.aliasPackageNames.any { it.lowercase().contains(query) } ||
                item.selected?.archivePath?.lowercase()?.contains(query) == true
            val matchesMultipleStyles = !iconPreferences.onlyShowMultipleStyles || item.variants.size >= 2
            val matchesUnadapted = !iconPreferences.onlyShowUnadaptedIcons || !item.adapted
            matchesSearch && matchesMultipleStyles && matchesUnadapted
        }

        val sorted = when (iconPreferences.sortField) {
            SortField.AppName -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
            SortField.PackageName -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.packageName })
        }
        return if (iconPreferences.descending) sorted.asReversed() else sorted
    }

    @Synchronized
    private fun loadLocalAppsSnapshot(): List<LocalAppInfo> {
        localAppsCache?.let { return it }
        val pm = getApplication<Application>().packageManager
        return runCatching {
            pm.getInstalledApplications(0).map { app ->
                LocalAppInfo(
                    packageName = app.packageName,
                    appName = app.loadLabel(pm).toString(),
                    system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
        }.getOrDefault(emptyList()).also { localAppsCache = it }
    }

    @Synchronized
    private fun invalidateLocalAppsCache() {
        localAppsCache = null
    }

    private fun variantOrder(variantKey: String, packageName: String): Int {
        if (variantKey == packageName) return 0
        return variantKey.removePrefix("${packageName}_").toIntOrNull() ?: Int.MAX_VALUE
    }

    // MARK: Project LAN sync

    fun startSyncServer() {
        stopSyncServer()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val token = java.util.UUID.randomUUID().toString().replace("-", "")
                val handler = ProjectSyncRouteHandler(
                    repository = repository,
                    resolveAppName = { packageNameRepository.resolveAppName(it, null) },
                    cacheDir = getApplication<Application>().cacheDir,
                    onPeerPaired = { announce ->
                        viewModelScope.launch(Dispatchers.Main) {
                            applyIncomingPeerAnnounce(announce)
                        }
                    },
                    onProjectEnsured = {
                        viewModelScope.launch(Dispatchers.Main) {
                            projects = repository.loadProjects()
                        }
                    },
                )
                val server = ProjectSyncHttpServer(
                    port = ProjectSyncConstants.DEFAULT_PORT,
                    token = token,
                    handler = handler::handle,
                )
                server.start()
                syncHttpServer = server
                withContext(Dispatchers.Main) {
                    syncServerRunning = true
                    syncServerPort = ProjectSyncConstants.DEFAULT_PORT
                    syncServerToken = token
                    syncLanAddress = ProjectSyncOrchestrator.preferredLanAddress()
                    syncStatusMessage = getApplication<Application>().getString(
                        R.string.sync_server_running,
                        syncLanAddress ?: "0.0.0.0",
                        syncServerPort,
                    )
                }
                if (hasSyncPeerConfigured()) {
                    registerSelfWithConfiguredPeer()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) { showError(error) }
            }
        }
    }

    fun applyIncomingPeerAnnounce(announce: ProjectSyncPeerAnnounce) {
        syncPeerHost = announce.host.trim()
        syncPeerPort = announce.port.toString()
        syncPeerToken = announce.token.trim()
        updateSettings(
            settings.copy(
                syncPeerHost = syncPeerHost,
                syncPeerPort = syncPeerPort.ifBlank { ProjectSyncConstants.DEFAULT_PORT.toString() },
                syncPeerToken = syncPeerToken,
            ),
        )
        syncStatusMessage = getApplication<Application>().getString(R.string.sync_mutual_paired)
    }

    private fun registerSelfWithConfiguredPeer() {
        viewModelScope.launch {
            val host = syncLanAddress
                ?: withContext(Dispatchers.IO) { ProjectSyncOrchestrator.preferredLanAddress() }
            if (!syncServerRunning || host.isNullOrBlank() || syncServerToken.isBlank()) return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    makeSyncClient().registerPeer(
                        ProjectSyncPeerAnnounce(
                            host = host,
                            port = syncServerPort,
                            token = syncServerToken,
                        ),
                    )
                }
                syncStatusMessage = getApplication<Application>().getString(R.string.sync_mutual_paired)
            }
        }
    }

    /**
     * Ensure this device is hosting so the remote can dial back, then announce ourselves to the peer.
     */
    fun ensureLocalServerAndRegisterWithPeer() {
        viewModelScope.launch {
            syncBusy = true
            try {
                if (!syncServerRunning) {
                    val started = withContext(Dispatchers.IO) {
                        runCatching {
                            val token = java.util.UUID.randomUUID().toString().replace("-", "")
                            val handler = ProjectSyncRouteHandler(
                                repository = repository,
                                resolveAppName = { packageNameRepository.resolveAppName(it, null) },
                                cacheDir = getApplication<Application>().cacheDir,
                                onPeerPaired = { announce ->
                                    viewModelScope.launch(Dispatchers.Main) {
                                        applyIncomingPeerAnnounce(announce)
                                    }
                                },
                                onProjectEnsured = {
                                    viewModelScope.launch(Dispatchers.Main) {
                                        projects = repository.loadProjects()
                                    }
                                },
                            )
                            stopSyncServer()
                            val server = ProjectSyncHttpServer(
                                port = ProjectSyncConstants.DEFAULT_PORT,
                                token = token,
                                handler = handler::handle,
                            )
                            server.start()
                            syncHttpServer = server
                            Triple(token, ProjectSyncConstants.DEFAULT_PORT, ProjectSyncOrchestrator.preferredLanAddress())
                        }.getOrElse { throw it }
                    }
                    syncServerRunning = true
                    syncServerToken = started.first
                    syncServerPort = started.second
                    syncLanAddress = started.third
                }
                val host = syncLanAddress
                    ?: withContext(Dispatchers.IO) { ProjectSyncOrchestrator.preferredLanAddress() }
                    ?: error(getApplication<Application>().getString(R.string.sync_no_lan_address))
                withContext(Dispatchers.IO) {
                    makeSyncClient().registerPeer(
                        ProjectSyncPeerAnnounce(
                            host = host,
                            port = syncServerPort,
                            token = syncServerToken,
                        ),
                    )
                }
                syncStatusMessage = getApplication<Application>().getString(R.string.sync_mutual_paired)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                showError(error)
            } finally {
                syncBusy = false
            }
        }
    }

    fun applySyncConnectionPayload(raw: String): Boolean {
        val parsed = ProjectSyncConnectionParser.parse(raw) ?: return false
        syncPeerHost = parsed.host
        syncPeerPort = parsed.port.toString()
        syncPeerToken = parsed.token
        updateSettings(
            settings.copy(
                syncPeerHost = syncPeerHost.trim(),
                syncPeerPort = syncPeerPort.trim().ifBlank { ProjectSyncConstants.DEFAULT_PORT.toString() },
                syncPeerToken = syncPeerToken.trim(),
            ),
        )
        syncStatusMessage = getApplication<Application>().getString(R.string.sync_scan_filled)
        ensureLocalServerAndRegisterWithPeer()
        return true
    }

    fun stopSyncServer() {
        syncHttpServer?.stop()
        syncHttpServer = null
        syncServerRunning = false
        syncStatusMessage = null
    }

    private fun makeSyncClient(): ProjectSyncClient {
        val host = syncPeerHost.trim()
        val token = syncPeerToken.trim()
        val port = syncPeerPort.trim().toIntOrNull() ?: ProjectSyncConstants.DEFAULT_PORT
        require(host.isNotBlank() && token.isNotBlank()) { "请填写对方主机、端口与令牌" }
        return ProjectSyncClient("http://$host:$port", token)
    }

    fun probeSyncPeer() {
        viewModelScope.launch {
            syncBusy = true
            try {
                val ok = withContext(Dispatchers.IO) { makeSyncClient().health() }
                syncStatusMessage = getApplication<Application>().getString(
                    if (ok) R.string.sync_status_ok else R.string.sync_status_fail,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                showError(error)
            } finally {
                syncBusy = false
            }
        }
    }

    fun buildSyncDiffFromPeer() {
        val id = selectedProjectId
        if (id == null) {
            showMessage(R.string.dialog_notice, getApplication<Application>().getString(R.string.sync_need_project))
            return
        }
        viewModelScope.launch {
            syncBusy = true
            try {
                val preview = withContext(Dispatchers.IO) {
                    val client = makeSyncClient()
                    val remoteProjects = client.listProjects()
                    if (remoteProjects.none { it.id == id }) {
                        withContext(Dispatchers.Main) {
                            syncStatusMessage = getApplication<Application>()
                                .getString(R.string.sync_creating_remote_project)
                        }
                        val localProject = repository.loadProjects().firstOrNull { it.id == id }
                            ?: error(getApplication<Application>().getString(R.string.sync_need_project))
                        client.ensureProject(localProject)
                    }
                    val local = repository.buildSyncInventory(id) { packageNameRepository.resolveAppName(it, null) }
                    val remote = client.inventory(id)
                    ProjectSyncDiffer.diff(local, remote)
                }
                syncDiffPreview = preview
                syncStatusMessage = if (preview.items.isEmpty()) {
                    getApplication<Application>().getString(R.string.sync_status_aligned)
                } else {
                    null
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                showError(error)
            } finally {
                syncBusy = false
            }
        }
    }

    fun toggleSyncDiffItem(index: Int) {
        val preview = syncDiffPreview ?: return
        val items = preview.items.toMutableList()
        if (index !in items.indices) return
        val item = items[index]
        items[index] = item.copy(selected = !item.selected)
        syncDiffPreview = preview.copy(items = items)
    }

    fun setSyncDiffAction(index: Int, action: ProjectSyncAction) {
        val preview = syncDiffPreview ?: return
        val items = preview.items.toMutableList()
        if (index !in items.indices) return
        items[index] = items[index].copy(action = action, selected = action != ProjectSyncAction.skip)
        syncDiffPreview = preview.copy(items = items)
    }

    fun selectSyncDiffSafe() {
        val preview = syncDiffPreview ?: return
        syncDiffPreview = preview.copy(
            items = preview.items.map { item ->
                item.copy(
                    selected = !item.isDeletionChoice &&
                        item.kind != com.bocchi.iconeditor.data.sync.ProjectSyncKind.bothChanged &&
                        item.kind != com.bocchi.iconeditor.data.sync.ProjectSyncKind.metadataChanged,
                )
            },
        )
    }

    fun selectSyncDiffNone() {
        val preview = syncDiffPreview ?: return
        syncDiffPreview = preview.copy(items = preview.items.map { it.copy(selected = false) })
    }

    fun selectSyncDiffPushLocalOnly() {
        val preview = syncDiffPreview ?: return
        syncDiffPreview = preview.copy(
            items = preview.items.map { item ->
                if (item.kind == ProjectSyncKind.missingOnRemote) {
                    item.copy(selected = true, action = ProjectSyncAction.pushToRemote)
                } else {
                    item
                }
            },
        )
    }

    fun selectSyncDiffDeleteLocalOnly() {
        val preview = syncDiffPreview ?: return
        syncDiffPreview = preview.copy(
            items = preview.items.map { item ->
                if (item.kind == ProjectSyncKind.missingOnRemote) {
                    item.copy(selected = true, action = ProjectSyncAction.deleteLocal)
                } else {
                    item
                }
            },
        )
    }

    fun selectSyncDiffPullRemoteOnly() {
        val preview = syncDiffPreview ?: return
        syncDiffPreview = preview.copy(
            items = preview.items.map { item ->
                if (item.kind == ProjectSyncKind.missingOnLocal) {
                    item.copy(selected = true, action = ProjectSyncAction.pullToLocal)
                } else {
                    item
                }
            },
        )
    }

    fun selectSyncDiffDeleteRemoteOnly() {
        val preview = syncDiffPreview ?: return
        syncDiffPreview = preview.copy(
            items = preview.items.map { item ->
                if (item.kind == ProjectSyncKind.missingOnLocal) {
                    item.copy(selected = true, action = ProjectSyncAction.deleteRemote)
                } else {
                    item
                }
            },
        )
    }

    fun dismissSyncDiff() {
        syncDiffPreview = null
    }

    fun applySyncDiff() {
        val id = selectedProjectId ?: return
        val preview = syncDiffPreview ?: return
        val total = preview.items.count { it.selected && it.action != ProjectSyncAction.skip }
        viewModelScope.launch {
            syncBusy = true
            syncApplyDone = 0
            syncApplyTotal = total
            syncStatusMessage = getApplication<Application>().getString(R.string.sync_status_applying)
            try {
                withContext(Dispatchers.IO) {
                    ProjectSyncOrchestrator.applySelected(
                        preview = preview,
                        client = makeSyncClient(),
                        repository = repository,
                        projectId = id,
                        cacheDir = getApplication<Application>().cacheDir,
                    ) { done, totalCount, label ->
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            syncApplyDone = done
                            syncApplyTotal = totalCount
                            syncStatusMessage = if (label.isBlank()) {
                                getApplication<Application>().getString(
                                    R.string.sync_status_progress,
                                    done,
                                    totalCount,
                                )
                            } else {
                                getApplication<Application>().getString(
                                    R.string.sync_status_progress_labeled,
                                    done,
                                    totalCount,
                                    label,
                                )
                            }
                        }
                    }
                }
                refresh()
                selectedProjectId?.let { loadProject(it, loadIcons = true) }
                syncDiffPreview = null
                syncStatusMessage = getApplication<Application>().getString(R.string.sync_status_done)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                showError(error)
            } finally {
                syncBusy = false
                syncApplyDone = 0
                syncApplyTotal = 0
            }
        }
    }

    private fun runAction(block: () -> Unit) {
        runCatching(block).onFailure(::showError)
    }

    private fun showError(error: Throwable) {
        showMessage(R.string.dialog_notice, error.message ?: error::class.java.simpleName)
    }

    private fun showMessage(titleRes: Int, summary: String) {
        message = AppMessage(getApplication<Application>().getString(titleRes), summary)
    }

    private data class InitialData(
        val projects: List<ProjectSummary>,
        val projectMetadata: Map<String, ProjectMetadata>,
    )

    private data class LoadedProject(
        val metadata: ProjectMetadata,
        val preferences: IconPreferences,
        val icons: List<IconAsset>,
        val localApps: List<LocalAppInfo>,
    )
}
