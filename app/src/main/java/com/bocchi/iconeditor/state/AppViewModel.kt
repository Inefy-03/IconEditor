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
import com.bocchi.iconeditor.data.InvalidIconPackApkException
import com.bocchi.iconeditor.data.InvalidProjectArchiveException
import com.bocchi.iconeditor.data.NoMaskLayersFoundException
import com.bocchi.iconeditor.data.ProjectRepository
import com.bocchi.iconeditor.data.packagename.PackageNameRepository
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

data class AppMessage(val title: String, val summary: String)

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
    var projectsScrollToTopRequest by mutableIntStateOf(0)
        private set
    var apkAssetsRevision by mutableIntStateOf(0)
        private set

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
        selectedProjectId?.let { loadProject(it, loadIcons = false) }
    }

    fun clearMessage() {
        message = null
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
        packageName: String,
        selectedVariantKey: String?,
        selectedAdditionIndex: Int?,
        replacements: List<Pair<IconAsset, Uri>>,
        additions: List<Uri>,
    ) = runAction {
        selectedProjectId?.let { projectId ->
            replacements.forEach { (asset, uri) ->
                repository.replaceIcon(projectId, asset, uri)
            }
            val addedVariantKeys = additions.map { uri ->
                repository.addIconVariant(projectId, packageName, uri)
            }
            val selectedBeforeNormalization = selectedAdditionIndex
                ?.let(addedVariantKeys::getOrNull)
                ?: selectedVariantKey
            val normalizedSelectedKey = repository.normalizeIconVariants(
                id = projectId,
                packageName = packageName,
                selectedVariantKey = selectedBeforeNormalization,
            )
            if (normalizedSelectedKey != null) {
                iconPreferences = iconPreferences.copy(
                    selectedVariants = iconPreferences.selectedVariants +
                        (packageName to normalizedSelectedKey),
                )
            } else {
                iconPreferences = iconPreferences.copy(
                    selectedVariants = iconPreferences.selectedVariants - packageName,
                )
            }
            repository.saveIconPreferences(projectId, iconPreferences)
            icons = repository.loadIcons(projectId)
            repository.syncIconMapping(projectId)
            refresh()
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
            repository.deleteIcon(it, asset)
            if (iconPreferences.selectedVariants[asset.packageName] == asset.variantKey) {
                iconPreferences = iconPreferences.copy(selectedVariants = iconPreferences.selectedVariants - asset.packageName)
                repository.saveIconPreferences(it, iconPreferences)
            }
            icons = repository.loadIcons(it)
            refresh()
        }
    }

    fun dismissExportProgress() {
        exportProgress = null
    }

    fun clearPendingApkInstall() {
        pendingApkInstallUri = null
    }

    fun exportProject(id: String, format: ExportFormat, target: Uri, locationLabel: String = "") {
        viewModelScope.launch {
            isExporting = true
            pendingApkInstallUri = null
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
                    pendingApkInstallUri = target
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
        repository.saveSettings(settings)
    }

    fun iconFile(asset: IconAsset) = selectedProjectId?.let { repository.iconFile(it, asset) }

    fun visibleIconItems(): List<IconListItem> {
        val localByPackage = localApps.associateBy { it.packageName }
        val grouped = icons.groupBy { it.packageName }
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
            IconListItem(
                packageName = packageName,
                appName = packageNameRepository.resolveAppName(packageName, local?.appName),
                variants = variants,
                selected = selected,
                localApp = local,
                adapted = variants.isNotEmpty(),
            )
        }.filter { item ->
            val matchesSearch = query.isBlank() ||
                item.packageName.lowercase().contains(query) ||
                item.appName.lowercase().contains(query) ||
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
