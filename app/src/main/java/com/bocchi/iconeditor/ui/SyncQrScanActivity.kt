package com.bocchi.iconeditor.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.data.sync.ProjectSyncConnectionParser
import com.bocchi.iconeditor.ui.component.ButtonLabel
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SyncQrScanActivity : ComponentActivity() {
    private var cameraProvider: ProcessCameraProvider? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var analysisExecutor: ExecutorService? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showScanner()
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> showScanner()
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showScanner() {
        if (barcodeScanner != null) return
        val scanner = runCatching {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            BarcodeScanning.getClient(options)
        }.getOrElse {
            showScannerUnavailable()
            return
        }
        val executor = Executors.newSingleThreadExecutor()
        barcodeScanner = scanner
        analysisExecutor = executor
        setContent {
            val controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.System) }
            MiuixTheme(controller = controller) {
                QrScannerScreen(
                    scanner = scanner,
                    analysisExecutor = executor,
                    onCameraProvider = { cameraProvider = it },
                    onScannerInitializationError = ::showScannerUnavailable,
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onRawValue = { raw ->
                        if (ProjectSyncConnectionParser.parse(raw) == null) return@QrScannerScreen false
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(EXTRA_RAW_VALUE, raw),
                        )
                        finish()
                        true
                    },
                )
            }
        }
    }

    private fun showScannerUnavailable() {
        if (isFinishing || isDestroyed) return
        Toast.makeText(this, R.string.sync_scan_unavailable, Toast.LENGTH_SHORT).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        runCatching { cameraProvider?.unbindAll() }
        runCatching { barcodeScanner?.close() }
        analysisExecutor?.shutdownNow()
        cameraProvider = null
        barcodeScanner = null
        analysisExecutor = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RAW_VALUE = "raw_value"
    }
}

@Composable
private fun QrScannerScreen(
    scanner: BarcodeScanner,
    analysisExecutor: ExecutorService,
    onCameraProvider: (ProcessCameraProvider) -> Unit,
    onScannerInitializationError: () -> Unit,
    onCancel: () -> Unit,
    onRawValue: (String) -> Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val handled = remember { AtomicBoolean(false) }
    var hint by remember { mutableStateOf(context.getString(R.string.sync_scan_camera_hint)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = runCatching {
                        ProcessCameraProvider.getInstance(ctx)
                    }.getOrElse {
                        previewView.post(onScannerInitializationError)
                        return@also
                    }
                    cameraProviderFuture.addListener(
                        {
                            runCatching {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setResolutionSelector(
                                        ResolutionSelector.Builder()
                                            .setResolutionStrategy(
                                                ResolutionStrategy(
                                                    Size(1280, 720),
                                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                                ),
                                            )
                                            .build(),
                                    )
                                    .build()
                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    if (handled.get()) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    val mediaImage = imageProxy.image
                                    if (mediaImage == null) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    val task = runCatching {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees,
                                        )
                                        scanner.process(image)
                                    }.getOrElse {
                                        imageProxy.close()
                                        previewView.post {
                                            hint = context.getString(R.string.sync_scan_unavailable)
                                        }
                                        return@setAnalyzer
                                    }
                                    val handleBarcodes: (List<Barcode>) -> Unit = { barcodes ->
                                        val raw = barcodes.firstOrNull()?.rawValue?.trim().orEmpty()
                                        if (raw.isNotEmpty() && handled.compareAndSet(false, true)) {
                                            previewView.post {
                                                if (!onRawValue(raw)) {
                                                    handled.set(false)
                                                    hint = context.getString(R.string.sync_scan_invalid)
                                                }
                                            }
                                        }
                                    }
                                    val handleFailure = {
                                        previewView.post {
                                            hint = context.getString(R.string.sync_scan_unavailable)
                                        }
                                    }
                                    if (context is Activity) {
                                        task.addOnSuccessListener(context) { barcodes -> handleBarcodes(barcodes) }
                                        task.addOnFailureListener(context) { handleFailure() }
                                    } else {
                                        task.addOnSuccessListener { barcodes -> handleBarcodes(barcodes) }
                                        task.addOnFailureListener { handleFailure() }
                                    }
                                    task.addOnCompleteListener { imageProxy.close() }
                                }
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                                onCameraProvider(cameraProvider)
                            }.onFailure {
                                previewView.post(onScannerInitializationError)
                            }
                        },
                        ContextCompat.getMainExecutor(ctx),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Text(
            text = hint,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(24.dp),
        )
        Button(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
        ) {
            ButtonLabel(stringResource(R.string.action_cancel))
        }
    }
}
