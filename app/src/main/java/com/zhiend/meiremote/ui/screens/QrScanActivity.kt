package com.zhiend.meiremote.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.zhiend.meiremote.ui.theme.LocalMeiColors
import java.util.concurrent.Executors

/** Activity that scans a Mei pairing QR and returns the code via RESULT_OK. */
class QrScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QrScanScreen(
                onCode = { code ->
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_CODE, code))
                    finish()
                },
                onClose = { finish() },
            )
        }
    }

    companion object {
        const val EXTRA_CODE = "pairing_code"
    }
}

@Composable
private fun QrScanScreen(onCode: (String) -> Unit, onClose: () -> Unit) {
    val c = LocalMeiColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var status by remember { mutableStateOf("Đang quét mã QR…") }
    val executor = remember { Executors.newSingleThreadExecutor() }

    val analyzer = remember {
        object : ImageAnalysis.Analyzer {
            var handled = false
            override fun analyze(image: ImageProxy) {
                if (handled) {
                    image.close()
                    return
                }
                val text = decodeQr(image)
                if (text != null && text.isNotBlank()) {
                    handled = true
                    onCode(text)
                }
                image.close()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (!granted) status = "Cần quyền camera để quét mã QR"
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.mainBg),
    ) {
        if (permissionGranted) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(executor, analyzer) }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(20.dp)
                .fillMaxWidth()
                .background(c.cardBg, RoundedCornerShape(14.dp))
                .border(1.dp, c.borderSoft, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Quét mã ghép nối", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "Mở MeiBrowser → Cài đặt → Mobile / Android bridge → Quick pairing, rồi quét mã QR đó.",
                color = c.textMuted,
                fontSize = 12.sp,
            )
            Text(status, color = c.accentHover, fontSize = 12.sp)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Hủy")
            }
        }
    }
}

private fun decodeQr(image: ImageProxy): String? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val width = image.width
    val height = image.height

    val data = ByteArray(width * height)
    val row = ByteArray(rowStride)
    var idx = 0
    for (y in 0 until height) {
        buffer.position(y * rowStride)
        buffer.get(row, 0, rowStride)
        if (pixelStride == 1) {
            System.arraycopy(row, 0, data, idx, width)
        } else {
            for (x in 0 until width) data[idx + x] = row[x * pixelStride]
        }
        idx += width
    }

    val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    return try {
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }.decode(bitmap)?.text
    } catch (_: Exception) {
        null
    }
}
