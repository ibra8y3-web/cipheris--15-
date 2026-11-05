package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.theme.SecureCyan
import com.example.ui.theme.SecureGreen
import com.example.ui.theme.TitaniumDark
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.MutedText
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun BarcodeScannerDialog(
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                Toast.makeText(context, "مطلوب إذن الكاميرا لمسح الرمز", Toast.LENGTH_LONG).show()
                onDismiss()
            }
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = TitaniumDark
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraScannerContent(
                        onDismiss = onDismiss,
                        onCodeScanned = { code ->
                            onCodeScanned(code)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CameraScannerContent(
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    var camera: Camera? by remember { mutableStateOf(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    
    val executor = remember { Executors.newSingleThreadExecutor() }

    // Pulse animation for scanner aiming square
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scanLineAnim by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Feed
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor, QrCodeAnalyzer { result ->
                        onCodeScanned(result)
                    })

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        Toast.makeText(context, "فشل في تشغيل الكاميرا", Toast.LENGTH_SHORT).show()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            }
        )

        // Futuristic Aiming HUD Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val boxSize = size.minDimension * 0.65f
            val left = (width - boxSize) / 2f
            val top = (height - boxSize) / 2f
            val right = left + boxSize
            val bottom = top + boxSize

            // 1. Semi-transparent black overlay outside the aiming square
            drawRect(
                color = Color.Black.copy(alpha = 0.65f)
            )
            
            // Clear the inner aiming area (cut hole)
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                cornerRadius = CornerRadius(16f, 16f),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )

            // 2. Aiming area bounds border
            drawRoundRect(
                color = SecureCyan.copy(alpha = 0.4f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Highlighted cyber corners
            val cornerLen = 30.dp.toPx()
            val thick = 4.dp.toPx()
            val greenColor = SecureGreen.copy(alpha = alphaAnim)

            // Top-Left
            drawLine(greenColor, Offset(left - 2, top - 2), Offset(left + cornerLen, top - 2), thick)
            drawLine(greenColor, Offset(left - 2, top - 2), Offset(left - 2, top + cornerLen), thick)

            // Top-Right
            drawLine(greenColor, Offset(right + 2, top - 2), Offset(right - cornerLen, top - 2), thick)
            drawLine(greenColor, Offset(right + 2, top - 2), Offset(right + 2, top + cornerLen), thick)

            // Bottom-Left
            drawLine(greenColor, Offset(left - 2, bottom + 2), Offset(left + cornerLen, bottom + 2), thick)
            drawLine(greenColor, Offset(left - 2, bottom + 2), Offset(left - 2, bottom - cornerLen), thick)

            // Bottom-Right
            drawLine(greenColor, Offset(right + 2, bottom + 2), Offset(right - cornerLen, bottom + 2), thick)
            drawLine(greenColor, Offset(right + 2, bottom + 2), Offset(right + 2, bottom - cornerLen), thick)

            // 4. Moving laser scanning line
            val lineY = top + (boxSize * scanLineAnim)
            drawLine(
                color = SecureGreen,
                start = Offset(left + 10.dp.toPx(), lineY),
                end = Offset(right - 10.dp.toPx(), lineY),
                strokeWidth = 2.5.dp.toPx()
            )
        }

        // Top UI Controls Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Text(
                text = "ماسح الرموز الضوئي",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            IconButton(
                onClick = {
                    val cameraControl = camera?.cameraControl
                    if (cameraControl != null) {
                        isFlashOn = !isFlashOn
                        cameraControl.enableTorch(isFlashOn)
                    }
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (isFlashOn) SecureGreen else Color.White
                )
            }
        }

        // Bottom Assistive Text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 50.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "وجه الكاميرا نحو باركود الصديق",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "سيقوم التطبيق بالتقاط الكود ومعالجة دعوة الاتصال تلقائياً",
                        color = MutedText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.ITF,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.PDF_417
            ),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    private var lastScannedTime = 0L

    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        // Throttle scans to once every 600ms for responsiveness
        if (currentTime - lastScannedTime < 600L) {
            image.close()
            return
        }

        try {
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val width = image.width
            val height = image.height

            // Extract Y channel correctly, compensating for rowStride alignment padding
            val data = if (rowStride == width) {
                val bytes = ByteArray(yBuffer.remaining())
                yBuffer.get(bytes)
                bytes
            } else {
                val bytes = ByteArray(width * height)
                for (row in 0 until height) {
                    yBuffer.position(row * rowStride)
                    yBuffer.get(bytes, row * width, width)
                }
                bytes
            }

            // Attempt 1: Normal orientation
            val source = PlanarYUVLuminanceSource(
                data,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decode(binaryBitmap)
                lastScannedTime = currentTime
                onQrCodeScanned(result.text)
                return
            } catch (e: Exception) {
                // Try rotation
            }

            // Attempt 2: Rotated 90 degrees clockwise (Common for portrait camera sensors)
            try {
                val rotatedData = rotateYUV420Degree90(data, width, height)
                val rotatedSource = PlanarYUVLuminanceSource(
                    rotatedData,
                    height,
                    width,
                    0,
                    0,
                    height,
                    width,
                    false
                )
                val rotatedBitmap = BinaryBitmap(HybridBinarizer(rotatedSource))
                val result = reader.decode(rotatedBitmap)
                lastScannedTime = currentTime
                onQrCodeScanned(result.text)
            } catch (e: Exception) {
                // Failed to decode both ways
            }
        } catch (e: Exception) {
            android.util.Log.e("QrCodeAnalyzer", "Error in barcode analysis: ${e.message}")
        } finally {
            image.close()
        }
    }

    private fun rotateYUV420Degree90(data: ByteArray, imageWidth: Int, imageHeight: Int): ByteArray {
        val yuv = ByteArray(imageWidth * imageHeight)
        var i = 0
        for (x in 0 until imageWidth) {
            for (y in imageHeight - 1 downTo 0) {
                yuv[i] = data[y * imageWidth + x]
                i++
            }
        }
        return yuv
    }
}
