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
import java.io.File
import java.util.zip.ZipFile

/**
 * 从 APK / 已解压的 MTZ·Module work 目录中提取 iconback / iconmask / iconupon。
 */
class MaskLayerExtractor(private val context: Context) {
    fun extractFromApk(apkFile: File, outputDir: File): Map<ApkPackAssets.MaskLayer, File> {
        outputDir.mkdirs()
        val loaded = loadApkResources(apkFile)
        val result = linkedMapOf<ApkPackAssets.MaskLayer, File>()
        ApkPackAssets.MaskLayer.entries.forEach { layer ->
            val bitmap = loadLayerBitmap(apkFile, loaded, layer) ?: return@forEach
            val target = File(outputDir, "${layer.resourceName}.png")
            target.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            result[layer] = target
        }
        return result
    }

    fun extractFromWorkDir(workDir: File, outputDir: File): Map<ApkPackAssets.MaskLayer, File> {
        outputDir.mkdirs()
        val result = linkedMapOf<ApkPackAssets.MaskLayer, File>()
        ApkPackAssets.MaskLayer.entries.forEach { layer ->
            val source = findLayerFile(workDir, layer.resourceName) ?: return@forEach
            val target = File(outputDir, "${layer.resourceName}.png")
            if (source.extension.equals("png", ignoreCase = true)) {
                source.copyTo(target, overwrite = true)
            } else {
                val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return@forEach
                target.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }
            result[layer] = target
        }
        return result
    }

    private fun loadLayerBitmap(
        apkFile: File,
        loaded: LoadedApkResources?,
        layer: ApkPackAssets.MaskLayer,
    ): Bitmap? {
        val names = when (layer) {
            ApkPackAssets.MaskLayer.Back -> listOf("iconback")
            ApkPackAssets.MaskLayer.Mask -> listOf("iconmask", "icon_mask", "mask")
            ApkPackAssets.MaskLayer.Upon -> listOf("iconupon")
        }
        if (loaded != null) {
            for (name in names) {
                loadDrawableBitmap(loaded, name)?.let { return it }
            }
        }
        val assetPaths = when (layer) {
            ApkPackAssets.MaskLayer.Back -> listOf("assets/iconback.png", "assets/iconback.webp")
            ApkPackAssets.MaskLayer.Mask -> listOf(
                "assets/iconmask.png",
                "assets/mask.png",
                "assets/icon_mask.png",
                "assets/iconmask.webp",
            )
            ApkPackAssets.MaskLayer.Upon -> listOf("assets/iconupon.png", "assets/iconupon.webp")
        }
        assetPaths.forEach { path ->
            loadBitmapFromZip(apkFile, path)?.let { return it }
        }
        return findDrawableInApkZip(apkFile, names)
    }

    private fun findLayerFile(root: File, resourceName: String): File? {
        val preferred = File(root, "icons/res/drawable-xxhdpi/$resourceName.png")
        if (preferred.isFile) return preferred
        val imageExtensions = setOf("png", "webp", "jpg", "jpeg")
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in imageExtensions }
            .filter { it.nameWithoutExtension.equals(resourceName, ignoreCase = true) }
            .sortedBy { pathPriority(it.invariantSeparatorsPath) }
            .firstOrNull()
    }

    private fun findDrawableInApkZip(apkFile: File, names: List<String>): Bitmap? {
        val nameSet = names.map { it.lowercase() }.toSet()
        val imageExtensions = setOf("png", "webp", "jpg", "jpeg")
        val matches = mutableListOf<String>()
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory || !entry.name.startsWith("res/")) return@forEach
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                if (extension !in imageExtensions) return@forEach
                val base = entry.name.substringAfterLast('/').substringBeforeLast('.')
                if (base.lowercase() in nameSet) {
                    matches += entry.name
                }
            }
        }
        val best = matches.minByOrNull(::pathPriority) ?: return null
        return loadBitmapFromZip(apkFile, best)
    }

    private fun pathPriority(path: String): Int = when {
        "xxhdpi" in path -> 0
        "xhdpi" in path -> 1
        "nodpi" in path -> 2
        "hdpi" in path -> 3
        "mdpi" in path -> 4
        else -> 5
    }

    private data class LoadedApkResources(
        val resources: Resources,
        val packageName: String,
    )

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

    private fun loadDrawableBitmap(loaded: LoadedApkResources, drawableName: String): Bitmap? {
        val resourceId = loaded.resources.getIdentifier(drawableName, "drawable", loaded.packageName)
        if (resourceId == 0) return null
        val drawable = runCatching { loaded.resources.getDrawable(resourceId, null) }.getOrNull()
            ?: return null
        return runCatching { drawableToBitmap(drawable) }.getOrNull()
    }

    private fun loadBitmapFromZip(apkFile: File, zipPath: String): Bitmap? = runCatching {
        ZipFile(apkFile).use { zip ->
            zip.getEntry(zipPath)?.let { entry ->
                zip.getInputStream(entry).use(BitmapFactory::decodeStream)
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
}
