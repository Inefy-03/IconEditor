package com.bocchi.iconeditor.data

import android.content.Context
import com.android.apksig.ApkSigner
import com.bocchi.iconeditor.model.ApkInfo
import com.bocchi.iconeditor.model.ExportPhase
import com.bocchi.iconeditor.model.IconAsset
import com.bocchi.iconeditor.model.IconMappingIndex
import java.io.File
import java.io.OutputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean

class ApkIconPackExporter(private val context: Context) {
    fun export(
        apkInfo: ApkInfo,
        workDir: File,
        mapping: IconMappingIndex,
        icons: List<IconAsset>,
        sourceApk: File?,
        output: OutputStream,
        reporter: ExportProgressReporter = ExportProgressReporter.NOOP,
    ) {
        if (sourceApk?.isFile == true) {
            reporter.log("忽略源 APK 结构，使用 aapt2 标准流程重建：${sourceApk.name}")
        }
        reporter.log("生成 appfilter.xml（${mapping.entries.size} 条映射）")
        val appfilter = IconMappingBridge.buildAppfilterXml(mapping.entries)
        val drawableXml = IconMappingBridge.buildDrawableXml(mapping.entries)
        val packableEntries = mapping.entries.filter { it.drawableName.isNotBlank() }

        val unsigned = File.createTempFile("iconeditor-apk-", ".apk", context.cacheDir)
        val signed = File.createTempFile("iconeditor-apk-signed-", ".apk", context.cacheDir)
        try {
            reporter.update(phase = ExportPhase.WritingArchive, detail = "aapt2 编译链接")
            reporter.log("开始 aapt2 编译链接（标准图标包结构）")
            Aapt2IconPackCompiler.buildUnsignedApk(
                output = unsigned,
                apkInfo = apkInfo,
                workDir = workDir,
                icons = icons,
                mapping = packableEntries,
                appfilter = appfilter,
                drawableXml = drawableXml,
                context = context,
                reporter = reporter,
            )
            reporter.update(phase = ExportPhase.Signing, detail = "v1/v2/v3 签名")
            reporter.log("开始 APK 签名")
            signApk(unsigned, signed, reporter)
            reporter.log("复制签名后的 APK 到目标")
            signed.inputStream().use { input -> input.copyTo(output) }
            reporter.log("APK 导出完成")
        } finally {
            unsigned.delete()
            signed.delete()
        }
    }

    private fun signApk(unsigned: File, signed: File, reporter: ExportProgressReporter) {
        val keystore = loadExportKeystore()
        val privateKey = keystore.getKey(KEY_ALIAS, KEY_PASSWORD) as java.security.PrivateKey
        val certificate = keystore.getCertificate(KEY_ALIAS) as X509Certificate
        reporter.log("使用 minSdkVersion=$ICON_PACK_MIN_SDK 签名")
        val stopHeartbeat = AtomicBoolean(false)
        val heartbeat = Thread {
            var tick = 0
            while (!stopHeartbeat.get()) {
                Thread.sleep(500)
                if (!stopHeartbeat.get()) {
                    reporter.log("签名进行中… (${++tick})")
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        try {
            ApkSigner.Builder(
                listOf(
                    ApkSigner.SignerConfig.Builder(
                        KEY_ALIAS,
                        privateKey,
                        listOf(certificate),
                    ).build(),
                ),
            )
                .setInputApk(unsigned)
                .setOutputApk(signed)
                .setMinSdkVersion(ICON_PACK_MIN_SDK)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign()
            reporter.log("签名完成")
        } finally {
            stopHeartbeat.set(true)
            heartbeat.join(1_000)
        }
    }

    private fun loadExportKeystore(): KeyStore {
        val cacheFile = File(context.filesDir, "apk_export_keystore.p12")
        if (cacheFile.isFile) {
            return KeyStore.getInstance(KEYSTORE_TYPE).apply {
                cacheFile.inputStream().use { load(it, KEYSTORE_PASSWORD) }
            }
        }

        val keystore = context.assets.open("archive_templates/apk/export_keystore.jks").use { stream ->
            KeyStore.getInstance(KEYSTORE_TYPE).apply {
                load(stream, KEYSTORE_PASSWORD)
            }
        }
        cacheFile.outputStream().use { output ->
            keystore.store(output, KEYSTORE_PASSWORD)
        }
        return keystore
    }

    private companion object {
        private const val KEYSTORE_TYPE = "PKCS12"
        private const val ICON_PACK_MIN_SDK = 21
        private val KEYSTORE_PASSWORD = "iconeditor".toCharArray()
        private val KEY_PASSWORD = "iconeditor".toCharArray()
        private const val KEY_ALIAS = "iconeditor_export"
    }
}
