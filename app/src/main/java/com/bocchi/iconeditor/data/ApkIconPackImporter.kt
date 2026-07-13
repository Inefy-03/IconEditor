package com.bocchi.iconeditor.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.bocchi.iconeditor.model.ApkInfo
import com.bocchi.iconeditor.model.ImportPhase
import com.bocchi.iconeditor.model.ImportProgress
import com.bocchi.iconeditor.model.IconMappingIndex
import com.bocchi.iconeditor.model.MtzInfo
import com.bocchi.iconeditor.model.ModuleInfo
import com.bocchi.iconeditor.model.ProjectMetadata
import java.io.File
import java.util.zip.ZipFile

class InvalidIconPackApkException : IllegalArgumentException()

data class ApkImportResult(
    val metadata: ProjectMetadata,
    val mapping: IconMappingIndex,
    val importedCount: Int,
    val skippedCount: Int,
)

private data class LoadedApkResources(
    val resources: Resources,
    val packageName: String,
)

class ApkIconPackImporter(private val context: Context) {
    fun hasAppfilter(apkFile: File): Boolean {
        if (hasAppfilterEntryInZip(apkFile)) return true
        return readAppfilterText(apkFile) != null
    }

    private fun hasAppfilterEntryInZip(apkFile: File): Boolean = runCatching {
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().any { entry ->
                !entry.isDirectory && (
                    entry.name == "assets/appfilter.xml" ||
                        entry.name == "assets/xml/appfilter.xml" ||
                        (entry.name.startsWith("res/") && entry.name.endsWith("/appfilter.xml"))
                    )
            }
        }
    }.getOrDefault(false)

    fun import(
        apkFile: File,
        workDir: File,
        sourceExtractDir: File,
        onProgress: (ImportProgress) -> Unit = {},
    ): ApkImportResult {
        onProgress(ImportProgress(ImportPhase.ParsingIcons, current = 0, total = 0))
        val appfilterText = readAppfilterText(apkFile) ?: throw InvalidIconPackApkException()
        val items = IconMappingBridge.parseAppfilterXml(appfilterText)
        if (items.isEmpty()) throw InvalidIconPackApkException()

        val deduped = IconMappingBridge.dedupeMappings(items)
        val loadedResources = loadApkResources(apkFile) ?: throw InvalidIconPackApkException()
        val zipPaths = findDrawableZipPaths(apkFile, deduped.map { it.drawableName }.toSet())
        val totalIcons = deduped.size

        workDir.deleteRecursively()
        sourceExtractDir.deleteRecursively()
        workDir.mkdirs()
        sourceExtractDir.mkdirs()
        ArchiveService.createDefaultWorkspace(workDir)

        val iconRoot = File(workDir, "icons/res/drawable-xxhdpi")
        iconRoot.mkdirs()
        val sourceIconRoot = File(sourceExtractDir, "icons/res/drawable-xxhdpi")
        sourceIconRoot.mkdirs()

        var importedCount = 0
        var skippedCount = 0
        deduped.forEachIndexed { index, mapping ->
            onProgress(ImportProgress(ImportPhase.ParsingIcons, current = index + 1, total = totalIcons))
            val bitmap = loadDrawableBitmap(
                apkFile = apkFile,
                loaded = loadedResources,
                drawableName = mapping.drawableName,
                zipPath = zipPaths[mapping.drawableName],
            )
            if (bitmap == null) {
                skippedCount++
                return@forEachIndexed
            }
            val target = File(iconRoot, "${mapping.packageName}.png")
            val sourceCopy = File(sourceIconRoot, "${mapping.packageName}.png")
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, sourceCopy.outputStream())
            importedCount++
        }

        if (importedCount == 0) throw InvalidIconPackApkException()

        extractApkLauncherAndMask(apkFile, workDir, loadedResources)

        val mappingIndex = IconMappingBridge.toMappingIndex(
            deduped = deduped,
            zipPaths = deduped.associate { mapping ->
                (mapping.packageName to mapping.drawableName) to
                    zipPaths[mapping.drawableName].orEmpty()
            },
        )
        val apkInfo = readApkInfo(apkFile)
        val metadata = ProjectMetadata(
            apk = apkInfo,
            mtz = MtzInfo(
                version = apkInfo.versionName,
                author = apkInfo.author,
                designer = apkInfo.author,
                title = apkInfo.label,
            ),
            module = ModuleInfo(
                id = apkInfo.packageName.filter { it.isLetterOrDigit() }.ifBlank { "IconPack" },
                name = apkInfo.label,
                author = apkInfo.author,
                version = apkInfo.versionName,
                theme = apkInfo.label.filter { it.isLetterOrDigit() }.ifBlank { apkInfo.label },
                themeId = apkInfo.packageName,
            ),
        )
        return ApkImportResult(
            metadata = metadata,
            mapping = mappingIndex,
            importedCount = importedCount,
            skippedCount = skippedCount,
        )
    }

    private fun readAppfilterText(apkFile: File): String? {
        readZipText(apkFile, "assets/appfilter.xml")?.let { return it }
        readZipText(apkFile, "assets/xml/appfilter.xml")?.let { return it }
        val loaded = loadApkResources(apkFile) ?: return null
        val resourceId = loaded.resources.getIdentifier("appfilter", "xml", loaded.packageName)
            .takeIf { it != 0 }
            ?: loaded.resources.getIdentifier("appfilter", "raw", loaded.packageName)
                .takeIf { it != 0 }
            ?: return null
        return runCatching {
            loaded.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun readZipText(apkFile: File, entryName: String): String? = runCatching {
        ZipFile(apkFile).use { zip ->
            zip.getEntry(entryName)?.let { entry ->
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        }
    }.getOrNull()

    private fun findDrawableZipPaths(apkFile: File, drawableNames: Set<String>): Map<String, String> {
        if (drawableNames.isEmpty()) return emptyMap()
        val imageExtensions = setOf("png", "webp", "jpg", "jpeg")
        val matches = mutableMapOf<String, MutableList<String>>()
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory || !entry.name.startsWith("res/")) return@forEach
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                if (extension !in imageExtensions) return@forEach
                val name = entry.name.substringAfterLast('/').substringBeforeLast('.')
                if (name in drawableNames) {
                    matches.getOrPut(name) { mutableListOf() } += entry.name
                }
            }
        }
        return matches.mapValues { (_, paths) -> paths.sortedBy(::drawablePathPriority).first() }
    }

    private fun drawablePathPriority(path: String): Int = when {
        "xxhdpi" in path -> 0
        "xhdpi" in path -> 1
        "nodpi" in path -> 2
        "hdpi" in path -> 3
        "mdpi" in path -> 4
        else -> 5
    }

    private fun loadApkResources(apkFile: File): LoadedApkResources? {
        val packageInfo = context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_ACTIVITIES,
        ) ?: return null
        val packageName = packageInfo.packageName?.takeIf { it.isNotBlank() } ?: return null
        packageInfo.applicationInfo?.let { appInfo ->
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                appInfo.splitSourceDirs = arrayOf(apkFile.absolutePath)
            }
        }
        val resources = runCatching {
            context.packageManager.getResourcesForApplication(packageInfo.applicationInfo!!)
        }.getOrElse {
            runCatching {
                val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
                AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                    .invoke(assetManager, apkFile.absolutePath)
                Resources(assetManager, context.resources.displayMetrics, context.resources.configuration)
            }.getOrNull()
        } ?: return null
        return LoadedApkResources(resources = resources, packageName = packageName)
    }

    private fun loadDrawableBitmap(
        apkFile: File,
        loaded: LoadedApkResources,
        drawableName: String,
        zipPath: String?,
    ): Bitmap? {
        val resourceId = loaded.resources.getIdentifier(drawableName, "drawable", loaded.packageName)
        if (resourceId != 0) {
            val drawable = runCatching { loaded.resources.getDrawable(resourceId, null) }.getOrNull()
            if (drawable != null) {
                return runCatching { drawableToBitmap(drawable) }.getOrNull()
            }
        }
        if (zipPath != null) {
            return loadBitmapFromZip(apkFile, zipPath)
        }
        return findDrawableZipPaths(apkFile, setOf(drawableName))[drawableName]
            ?.let { loadBitmapFromZip(apkFile, it) }
    }

    private fun loadBitmapFromZip(apkFile: File, zipPath: String): Bitmap? = runCatching {
        ZipFile(apkFile).use { zip ->
            zip.getEntry(zipPath)?.let { entry ->
                zip.getInputStream(entry).use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        }
    }.getOrNull()

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.copy(drawable.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun extractApkLauncherAndMask(
        apkFile: File,
        workDir: File,
        loaded: LoadedApkResources,
    ) {
        val apkDir = File(workDir, "apk").also { it.mkdirs() }
        val iconRoot = File(workDir, "icons/res/drawable-xxhdpi").also { it.mkdirs() }
        loadApplicationIconBitmap(apkFile)?.let { bitmap ->
            File(apkDir, "ic_launcher.png").outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
        fun saveLayer(fileName: String, bitmap: Bitmap?) {
            if (bitmap == null) return
            File(iconRoot, "$fileName.png").outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
        saveLayer(
            "iconback",
            loadDrawableBitmap(apkFile, loaded, "iconback", zipPath = null)
                ?: loadBitmapFromZip(apkFile, "assets/iconback.png"),
        )
        saveLayer(
            "iconmask",
            loadDrawableBitmap(apkFile, loaded, "iconmask", zipPath = null)
                ?: loadDrawableBitmap(apkFile, loaded, "icon_mask", zipPath = null)
                ?: loadBitmapFromZip(apkFile, "assets/iconmask.png")
                ?: loadBitmapFromZip(apkFile, "assets/mask.png")
                ?: loadBitmapFromZip(apkFile, "assets/icon_mask.png"),
        )
        saveLayer(
            "iconupon",
            loadDrawableBitmap(apkFile, loaded, "iconupon", zipPath = null)
                ?: loadBitmapFromZip(apkFile, "assets/iconupon.png"),
        )
    }

    private fun loadApplicationIconBitmap(apkFile: File): Bitmap? = runCatching {
        val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        val appInfo = packageInfo?.applicationInfo ?: return null
        appInfo.sourceDir = apkFile.absolutePath
        appInfo.publicSourceDir = apkFile.absolutePath
        drawableToBitmap(context.packageManager.getApplicationIcon(appInfo))
    }.getOrNull()

    private fun readApkInfo(apkFile: File): ApkInfo {
        val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        val appInfo = packageInfo?.applicationInfo
        if (appInfo != null) {
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath
        }
        val label = appInfo?.let {
            context.packageManager.getApplicationLabel(it).toString()
        }.orEmpty()
        return ApkInfo(
            packageName = packageInfo?.packageName.orEmpty(),
            versionName = packageInfo?.versionName.orEmpty(),
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode?.toInt() ?: 1
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode ?: 1
            },
            label = label,
        )
    }
}
