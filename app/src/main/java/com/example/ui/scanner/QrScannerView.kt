package com.example.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.SalimBlue
import com.example.ui.theme.SalimNearBlack
import com.example.ui.theme.SalimSecondaryText
import com.example.ui.theme.SalimWhite
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@Composable
fun QrScannerView(
    modifier: Modifier = Modifier,
    onQrCodeScanned: (String) -> Unit,
    onEnterManuallyClicked: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionDeniedCard(
            onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
            onEnterManually = onEnterManuallyClicked,
            modifier = modifier
        )
    } else {
        CameraScannerSurface(
            onQrCodeScanned = onQrCodeScanned,
            onEnterManuallyClicked = onEnterManuallyClicked,
            modifier = modifier
        )
    }
}

@Composable
private fun CameraPermissionDeniedCard(
    onRequestPermission: () -> Unit,
    onEnterManually: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SalimWhite)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Camera Permission",
                tint = SalimBlue,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Camera Access Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SalimNearBlack
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Salim uses your camera exclusively to scan Ubaid's pairing QR code securely. No photos or video are recorded.",
                fontSize = 14.sp,
                color = SalimSecondaryText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = SalimBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Grant Camera Permission", fontWeight = FontWeight.SemiBold, color = SalimWhite)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onEnterManually,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = SalimBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Enter Pairing Code Manually", color = SalimBlue, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CameraScannerSurface(
    onQrCodeScanned: (String) -> Unit,
    onEnterManuallyClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var isCameraBound by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var hasDetectedQr by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    val transition = rememberInfiniteTransition(label = "scan_laser")
    val laserOffsetRatio by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (cameraError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SalimWhite)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Camera Unavailable",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SalimNearBlack
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cameraError ?: "Could not start camera preview.",
                        fontSize = 14.sp,
                        color = SalimSecondaryText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onEnterManuallyClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = SalimBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Enter Pairing Code Manually", color = SalimWhite)
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val reader = MultiFormatReader().apply {
                                val hints = mapOf(
                                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                    DecodeHintType.TRY_HARDER to true
                                )
                                setHints(hints)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                if (hasDetectedQr) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                processImageProxy(imageProxy, reader) { scannedText ->
                                    if (!hasDetectedQr && scannedText.isNotBlank()) {
                                        hasDetectedQr = true
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            onQrCodeScanned(scannedText)
                                        }
                                    }
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            isCameraBound = true
                        } catch (e: Exception) {
                            Log.e("QrScannerView", "Camera binding failed: ${e.message}", e)
                            cameraError = "Camera hardware error: ${e.localizedMessage}"
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // High-contrast clean Viewfinder Cutout Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxSize = size.minDimension * 0.72f
                val left = (size.width - boxSize) / 2f
                val top = (size.height - boxSize) / 2f * 0.82f
                val rect = Rect(Offset(left, top), Size(boxSize, boxSize))

                val backgroundPath = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                }
                val cutoutPath = Path().apply {
                    addRoundRect(RoundRect(rect, CornerRadius(24.dp.toPx(), 24.dp.toPx())))
                }

                // Darken outside viewfinder
                clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                    drawRect(Color(0x99000000))
                }

                // High-contrast white border around scan cutout
                drawRoundRect(
                    color = Color.White,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )

                // Blue corner highlights
                val cornerLength = 32.dp.toPx()
                val cornerStroke = 5.dp.toPx()

                // Top-Left
                drawLine(SalimBlue, Offset(left, top + 20), Offset(left + cornerLength, top + 20), cornerStroke)
                drawLine(SalimBlue, Offset(left + 20, top), Offset(left + 20, top + cornerLength), cornerStroke)

                // Top-Right
                drawLine(SalimBlue, Offset(left + boxSize - cornerLength, top + 20), Offset(left + boxSize, top + 20), cornerStroke)
                drawLine(SalimBlue, Offset(left + boxSize - 20, top), Offset(left + boxSize - 20, top + cornerLength), cornerStroke)

                // Bottom-Left
                drawLine(SalimBlue, Offset(left, top + boxSize - 20), Offset(left + cornerLength, top + boxSize - 20), cornerStroke)
                drawLine(SalimBlue, Offset(left + 20, top + boxSize - cornerLength), Offset(left + 20, top + boxSize), cornerStroke)

                // Bottom-Right
                drawLine(SalimBlue, Offset(left + boxSize - cornerLength, top + boxSize - 20), Offset(left + boxSize, top + boxSize - 20), cornerStroke)
                drawLine(SalimBlue, Offset(left + boxSize - 20, top + boxSize - cornerLength), Offset(left + boxSize - 20, top + boxSize), cornerStroke)

                // Animated scan line
                val laserY = top + (boxSize * laserOffsetRatio)
                drawLine(
                    color = SalimBlue,
                    start = Offset(left + 16.dp.toPx(), laserY),
                    end = Offset(left + boxSize - 16.dp.toPx(), laserY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}

private fun processImageProxy(
    image: ImageProxy,
    reader: MultiFormatReader,
    onSuccess: (String) -> Unit
) {
    if (image.format != ImageFormat.YUV_420_888 && image.format != ImageFormat.YUV_422_888 && image.format != ImageFormat.YUV_444_888) {
        image.close()
        return
    }

    try {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        val source = PlanarYUVLuminanceSource(
            bytes,
            rowStride,
            height,
            0,
            0,
            width,
            height,
            false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decodeWithState(bitmap)
        if (result != null && result.text != null) {
            onSuccess(result.text)
        }
    } catch (_: Exception) {
        // Expected when frame doesn't contain a readable QR code yet
    } finally {
        reader.reset()
        image.close()
    }
}
