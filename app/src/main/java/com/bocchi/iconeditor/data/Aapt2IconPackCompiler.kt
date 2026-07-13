package com.bocchi.iconeditor.data

import android.content.Context
import com.bocchi.iconeditor.model.ApkInfo
import com.bocchi.iconeditor.model.ExportPhase
import com.bocchi.iconeditor.model.IconAsset
import com.bocchi.iconeditor.model.IconMappingEntry
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 使用 aapt2 compile + link 构建图标包 APK（与 CandyBar / Gradle 标准流程一致）。
 *
 * 编译加速：
 * - `--no-crunch`：跳过 PNG 再压缩（图标已是成品 PNG）
 * - 多进程分片并行 compile（利用多核）
 */
object Aapt2IconPackCompiler {
    private const val DRAWABLE_SHARD_SIZE = 120
    private const val MAX_COMPILE_WORKERS = 8

    fun buildUnsignedApk(
        output: File,
        apkInfo: ApkInfo,
        workDir: File,
        icons: List<IconAsset>,
        mapping: List<IconMappingEntry>,
        appfilter: String,
        drawableXml: String,
        context: Context? = null,
        cacheDir: File? = null,
        reporter: ExportProgressReporter = ExportProgressReporter.NOOP,
        toolchainOverride: Aapt2Toolchain.Resolved? = null,
    ) {
        val stagingParent = cacheDir ?: context?.cacheDir
            ?: error("需要提供 Context 或 cacheDir")
        val toolchain = toolchainOverride
            ?: Aapt2Toolchain(requireNotNull(context) { "需要提供 Context 以解析 aapt2 工具链" }).resolve().also {
                reporter.log("aapt2：${it.aapt2.absolutePath}")
                reporter.log("android.jar：${it.androidJar.absolutePath}")
                reporter.log("启动方式：直接执行（静态 aapt2）")
            }
        val stagingRoot = File(stagingParent, "iconpack-aapt2-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            reporter.update(phase = ExportPhase.WritingArchive, detail = "准备 aapt2 资源目录")
            reporter.log("aapt2 工具：${toolchain.aapt2.name}")
            stageResources(
                stagingRoot = stagingRoot,
                apkInfo = apkInfo,
                workDir = workDir,
                icons = icons,
                mapping = mapping,
                appfilter = appfilter,
                drawableXml = drawableXml,
                reporter = reporter,
            )
            val compiledArchives = compileResourcesParallel(
                stagingRoot = stagingRoot,
                toolchain = toolchain,
                reporter = reporter,
            )
            val manifestFile = File(stagingRoot, "AndroidManifest.xml")
            val assetsDir = File(stagingRoot, "assets")
            reporter.update(phase = ExportPhase.WritingArchive, detail = "aapt2 link")
            runAapt2(
                reporter = reporter,
                label = "aapt2 link",
                toolchain = toolchain,
                args = listOf(
                    "link",
                    "-o", output.absolutePath,
                    "--manifest", manifestFile.absolutePath,
                    "-I", toolchain.androidJar.absolutePath,
                    "-A", assetsDir.absolutePath,
                    "--min-sdk-version", "21",
                    "--target-sdk-version", "34",
                    "--version-code", apkInfo.versionCode.coerceAtLeast(1).toString(),
                    "--version-name", apkInfo.versionName.ifBlank { "1.0" },
                ) + compiledArchives.map { it.absolutePath },
            )
            reporter.log("aapt2 link 完成：${output.length()} 字节")
            require(output.isFile && output.length() > 0L) { "aapt2 未生成有效 APK" }
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    private fun stageResources(
        stagingRoot: File,
        apkInfo: ApkInfo,
        workDir: File,
        icons: List<IconAsset>,
        mapping: List<IconMappingEntry>,
        appfilter: String,
        drawableXml: String,
        reporter: ExportProgressReporter,
    ) {
        val resRoot = File(stagingRoot, "res").apply { mkdirs() }
        File(resRoot, "drawable-nodpi").mkdirs()
        File(resRoot, "xml").mkdirs()
        File(resRoot, "values").mkdirs()
        val assetsDir = File(stagingRoot, "assets").apply { mkdirs() }

        File(stagingRoot, "AndroidManifest.xml").writeText(IconPackManifestBuilder.build(apkInfo))
        File(resRoot, "values/strings.xml").writeText(IconPackManifestBuilder.buildStringsXml(apkInfo))
        File(resRoot, "xml/appfilter.xml").writeText(appfilter)
        File(resRoot, "xml/drawable.xml").writeText(drawableXml)
        File(assetsDir, "appfilter.xml").writeText(appfilter)
        File(assetsDir, "drawable.xml").writeText(drawableXml)
        reporter.log("已写入 appfilter.xml / drawable.xml（res + assets）")

        val packable = mapping.filter { it.drawableName.isNotBlank() }
        val total = packable.size
        reporter.update(phase = ExportPhase.PackagingIcons, total = total, current = 0)
        var current = 0
        var skipped = 0
        packable.forEach { entry ->
            val source = IconMappingBridge.resolveIconFile(workDir, entry, icons)
            if (source == null || !source.isFile) {
                skipped++
                if (skipped <= 5 || skipped % 50 == 0) {
                    reporter.log("跳过缺失图标：${entry.packageName} -> ${entry.drawableName}")
                }
                return@forEach
            }
            val target = File(resRoot, "drawable-nodpi/${entry.drawableName}.png")
            hardLinkOrCopy(source, target)
            current++
            if (current == 1 || current == total || current % 50 == 0) {
                reporter.update(
                    phase = ExportPhase.PackagingIcons,
                    current = current,
                    total = total,
                    detail = "${entry.packageName} -> ${entry.drawableName}",
                    log = "准备图标 $current/$total",
                )
            } else {
                reporter.update(
                    phase = ExportPhase.PackagingIcons,
                    current = current,
                    total = total,
                    detail = "${entry.packageName} -> ${entry.drawableName}",
                )
            }
        }
        reporter.log("资源目录就绪：$current 个 drawable，跳过 $skipped 个")
    }

    private fun compileResourcesParallel(
        stagingRoot: File,
        toolchain: Aapt2Toolchain.Resolved,
        reporter: ExportProgressReporter,
    ): List<File> {
        val resRoot = File(stagingRoot, "res")
        val drawableDir = File(resRoot, "drawable-nodpi")
        val drawables = drawableDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        val compiledDir = File(stagingRoot, "compiled").apply { mkdirs() }
        val baseRes = File(stagingRoot, "res-base").apply { mkdirs() }
        File(baseRes, "values").mkdirs()
        File(baseRes, "xml").mkdirs()
        File(resRoot, "values/strings.xml").copyTo(File(baseRes, "values/strings.xml"), overwrite = true)
        File(resRoot, "xml/appfilter.xml").copyTo(File(baseRes, "xml/appfilter.xml"), overwrite = true)
        File(resRoot, "xml/drawable.xml").copyTo(File(baseRes, "xml/drawable.xml"), overwrite = true)

        val baseZip = File(compiledDir, "base.zip")
        reporter.update(phase = ExportPhase.WritingArchive, detail = "aapt2 compile（并行）")
        reporter.log("编译基础资源（xml/values）")
        runAapt2(
            reporter = reporter,
            label = "aapt2 compile base",
            toolchain = toolchain,
            heartbeatIntervalMs = 2_000,
            args = listOf(
                "compile",
                "--no-crunch",
                "--dir", baseRes.absolutePath,
                "-o", baseZip.absolutePath,
            ),
        )

        if (drawables.isEmpty()) return listOf(baseZip)

        val workerCount = min(
            MAX_COMPILE_WORKERS,
            max(1, Runtime.getRuntime().availableProcessors()),
        )
        val shardCount = max(
            1,
            min(
                workerCount,
                ceil(drawables.size / DRAWABLE_SHARD_SIZE.toDouble()).toInt(),
            ),
        )
        val shardSize = ceil(drawables.size / shardCount.toDouble()).toInt()
            reporter.log("并行编译 drawable：${drawables.size} 个，分 $shardCount 片，最多 $workerCount 线程")

        val completedShards = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(min(workerCount, shardCount))
        val errors = mutableListOf<Throwable>()
        val archives = mutableListOf(baseZip)
        try {
            val futures = ArrayList<Future<File>>()
            for (shardIndex in 0 until shardCount) {
                val from = shardIndex * shardSize
                if (from >= drawables.size) break
                val to = min(drawables.size, from + shardSize)
                val shardFiles = drawables.subList(from, to)
                futures += executor.submit<File> {
                    val shardRes = File(stagingRoot, "res-shard-$shardIndex").apply { mkdirs() }
                    val shardDrawable = File(shardRes, "drawable-nodpi").apply { mkdirs() }
                    shardFiles.forEach { src ->
                        hardLinkOrCopy(src, File(shardDrawable, src.name))
                    }
                    val shardZip = File(compiledDir, "drawable-$shardIndex.zip")
                    runAapt2(
                        reporter = reporter,
                        label = "aapt2 compile shard${shardIndex + 1}/$shardCount",
                        toolchain = toolchain,
                        heartbeatIntervalMs = 2_500,
                        args = listOf(
                            "compile",
                            "--no-crunch",
                            "--dir", shardRes.absolutePath,
                            "-o", shardZip.absolutePath,
                        ),
                    )
                    val done = completedShards.incrementAndGet()
                    reporter.update(
                        phase = ExportPhase.WritingArchive,
                        detail = "编译分片 $done/$shardCount",
                        log = "分片 ${shardIndex + 1}/$shardCount 完成（${shardFiles.size} 图标）",
                    )
                    shardZip
                }
            }
            futures.forEach { future ->
                try {
                    archives += future.get()
                } catch (error: Throwable) {
                    errors += error.cause ?: error
                }
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(30, TimeUnit.MINUTES)
        }
        if (errors.isNotEmpty()) {
            throw errors.first()
        }
        reporter.log("aapt2 compile 全部完成：${archives.size} 个资源包")
        return archives
    }

    private fun hardLinkOrCopy(source: File, target: File) {
        if (target.exists()) target.delete()
        target.parentFile?.mkdirs()
        val linked = runCatching {
            Files.createLink(target.toPath(), source.toPath())
            true
        }.getOrDefault(false)
        if (!linked) {
            source.copyTo(target, overwrite = true)
        }
    }

    private fun runAapt2(
        reporter: ExportProgressReporter,
        label: String,
        toolchain: Aapt2Toolchain.Resolved,
        args: List<String>,
        heartbeatIntervalMs: Long = 2_000,
    ) {
        reporter.log("$label 开始")
        val command = toolchain.command(args)
        reporter.log("命令：${command.take(6).joinToString(" ")} …")
        val stopHeartbeat = AtomicBoolean(false)
        val heartbeat = Thread {
            var tick = 0
            while (!stopHeartbeat.get()) {
                Thread.sleep(heartbeatIntervalMs)
                if (!stopHeartbeat.get()) {
                    reporter.log("$label 进行中… (${++tick})")
                }
            }
        }.apply {
            isDaemon = true
            name = "aapt2-heartbeat-$label"
            start()
        }
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    output.appendLine(line)
                    if (line.isNotBlank()) {
                        reporter.log("$label > $line")
                    }
                    line = reader.readLine()
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error("$label 失败 (exit $exitCode):\n$output")
            }
        } finally {
            stopHeartbeat.set(true)
            heartbeat.join(1_000)
        }
    }
}
