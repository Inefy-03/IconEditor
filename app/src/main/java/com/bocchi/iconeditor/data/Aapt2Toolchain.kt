package com.bocchi.iconeditor.data

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * 解析设备端 aapt2 与 android.jar。
 *
 * aapt2 is a 16 KB-aligned PIE packaged as a JNI library and extracted to
 * nativeLibraryDir. That directory is readable and executable, so the app can
 * launch it directly without copying an executable into files/cache.
 */
class Aapt2Toolchain(private val context: Context) {
    data class Resolved(
        val aapt2: File,
        val androidJar: File,
    ) {
        fun command(args: List<String>): List<String> = listOf(aapt2.absolutePath) + args
    }

    fun resolve(): Resolved {
        resolveHostToolchain()?.let { return it }
        return resolveBundledToolchain()
    }

    private fun resolveBundledToolchain(): Resolved {
        val dataDir = File(context.filesDir, "export_toolchain").apply { mkdirs() }
        val androidJar = ensureAndroidJar(dataDir)
        val aapt2 = resolveDeviceAapt2Binary()
            ?: error(
                "未找到 libiconeditor_aapt2.so，请重新安装最新版 APK。" +
                    " nativeLibraryDir=${context.applicationInfo.nativeLibraryDir}",
            )
        prepareExecutable(aapt2)
        val resolved = Resolved(aapt2 = aapt2, androidJar = androidJar)
        verifyAapt2Runnable(resolved)
        return resolved
    }

    private fun resolveDeviceAapt2Binary(): File? {
        val candidates = buildList {
            context.applicationInfo.nativeLibraryDir?.takeIf { it.isNotBlank() }?.let { dir ->
                add(File(dir, "libiconeditor_aapt2.so"))
            }
            val sourceDir = context.applicationInfo.sourceDir
            if (!sourceDir.isNullOrBlank()) {
                val appLibRoot = File(sourceDir).parentFile
                if (appLibRoot != null) {
                    add(File(appLibRoot, "lib/arm64/libiconeditor_aapt2.so"))
                    add(File(appLibRoot, "lib/arm64-v8a/libiconeditor_aapt2.so"))
                    add(File(appLibRoot, "lib/x86_64/libiconeditor_aapt2.so"))
                }
            }
        }
        return candidates.firstOrNull { it.isFile && it.length() > 0L }
    }

    private fun prepareExecutable(binary: File) {
        runCatching { binary.setReadable(true) }
        runCatching { binary.setExecutable(true, false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching {
                Os.chmod(binary.absolutePath, 0b111_101_101) // 0755
            }
        }
    }

    private fun ensureAndroidJar(dataDir: File): File {
        val target = File(dataDir, "android-35.jar")
        if (isCompactFrameworkJar(target)) return target
        target.delete()
        val assetCandidates = listOf(
            "export_toolchain/android-35.jar",
            "export_toolchain/android-35.jar.gz",
        )
        var lastError: Exception? = null
        for (assetPath in assetCandidates) {
            try {
                context.assets.open(assetPath).use { input ->
                    if (assetPath.endsWith(".gz")) {
                        GZIPInputStream(input).use { gzip ->
                            target.outputStream().use { output -> gzip.copyTo(output) }
                        }
                    } else {
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                if (isCompactFrameworkJar(target)) return target
                target.delete()
                lastError = IllegalStateException("Invalid compact framework resource jar: $assetPath")
            } catch (error: Exception) {
                target.delete()
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("无法加载 export_toolchain/android-35.jar")
    }

    private fun isCompactFrameworkJar(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() <= 0L) return@runCatching false
        ZipFile(file).use { zip ->
            val names = buildSet {
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory) add(entry.name)
                }
            }
            names == CompactFrameworkEntries
        }
    }.getOrDefault(false)

    private fun verifyAapt2Runnable(resolved: Resolved) {
        val process = ProcessBuilder(resolved.command(listOf("version")))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            error("aapt2 无法运行 (exit $exitCode): $output")
        }
    }

    companion object {
        private val CompactFrameworkEntries = setOf("AndroidManifest.xml", "resources.arsc")

        private val preferredBuildToolsVersions = listOf(
            "34.0.0",
            "35.0.0",
            "36.0.0",
            "36.1.0",
            "37.0.0",
        )

        fun resolveHostToolchain(): Resolved? {
            val sdkRoots = listOfNotNull(
                System.getenv("ANDROID_HOME"),
                System.getenv("ANDROID_SDK_ROOT"),
                "${System.getProperty("user.home")}/Library/Android/sdk",
            ).map(::File).filter { it.isDirectory }
            for (sdk in sdkRoots) {
                val buildTools = sdk.resolve("build-tools")
                val androidJar = sequenceOf("android-35", "android-36", "android-36.1", "android-37", "android-37.0", "android-34")
                    .map { sdk.resolve("platforms/$it/android.jar") }
                    .firstOrNull { it.isFile }
                    ?: continue
                val aapt2 = orderedBuildToolsDirs(buildTools)
                    .mapNotNull { dir -> dir.resolve("aapt2").takeIf { it.isFile && it.canExecute() } }
                    .firstOrNull { isWorkingAapt2(it) }
                    ?: continue
                return Resolved(aapt2 = aapt2, androidJar = androidJar)
            }
            return null
        }

        private fun orderedBuildToolsDirs(buildTools: File): List<File> {
            val dirs = buildTools.listFiles()?.filter { it.isDirectory }.orEmpty()
            return dirs.sortedWith(
                compareBy<File> { dir ->
                    val index = preferredBuildToolsVersions.indexOf(dir.name)
                    if (index < 0) Int.MAX_VALUE else index
                }.thenByDescending { it.name },
            )
        }

        private fun isWorkingAapt2(binary: File): Boolean = runCatching {
            val probeRoot = File.createTempFile("aapt2-probe-", null).apply { delete(); mkdirs() }
            try {
                val resDir = File(probeRoot, "res/values").apply { mkdirs() }
                File(resDir, "strings.xml").writeText(
                    """<?xml version="1.0" encoding="utf-8"?><resources />""",
                )
                val output = File(probeRoot, "compiled.zip")
                val process = ProcessBuilder(
                    binary.absolutePath,
                    "compile",
                    "--dir",
                    File(probeRoot, "res").absolutePath,
                    "-o",
                    output.absolutePath,
                )
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.use { it.readBytes() }
                process.waitFor() == 0 && output.isFile && output.length() > 0L
            } finally {
                probeRoot.deleteRecursively()
            }
        }.getOrDefault(false)
    }
}
