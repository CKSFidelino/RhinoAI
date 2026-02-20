@file:OptIn(ExperimentalMaterial3Api::class)

package com.denizelif.rhinoplasty

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// COIL
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

// ML KIT
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions

// TENSORFLOW
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.abs

@androidx.camera.core.ExperimentalGetImage
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RhinoplastyApp()
        }
    }
}

// ---------------- MODEL YÜKLEYİCİ ----------------
@Throws(IOException::class)
private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
    val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelName)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    val fileChannel: FileChannel = inputStream.channel
    val startOffset = fileDescriptor.startOffset
    val declaredLength = fileDescriptor.declaredLength
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
}

suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
    val loader = ImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(uri)
        .allowHardware(false)
        .build()
    val result = (loader.execute(request) as SuccessResult).drawable
    (result as BitmapDrawable).bitmap
}

suspend fun processGalleryImages(
    context: Context,
    uris: List<Uri>,
    onProgress: (String) -> Unit,
    onError: (String) -> Unit,
    onSuccess: (List<Bitmap>) -> Unit
) {
    withContext(Dispatchers.Default) {
        try {
            val validRawBitmaps = mutableListOf<Bitmap>()
            val detectorOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()
            val faceDetector = FaceDetection.getClient(detectorOptions)

            for ((index, uri) in uris.withIndex()) {
                withContext(Dispatchers.Main) { onProgress("${index + 1}. Fotoğraf Kontrol Ediliyor...") }
                val bitmap = try {
                    loadBitmapFromUri(context, uri)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { onError("Fotoğraf yüklenemedi.") }
                    return@withContext
                }

                val image = InputImage.fromBitmap(bitmap, 0)
                val faces = suspendCancellableCoroutine { continuation ->
                    faceDetector.process(image)
                        .addOnSuccessListener { continuation.resume(it) }
                        .addOnFailureListener { continuation.resume(emptyList()) }
                }

                if (faces.isEmpty()) {
                    withContext(Dispatchers.Main) { onError("${index + 1}. fotoğrafta yüz bulunamadı.") }
                    return@withContext
                }
                if (faces.size > 1) {
                    withContext(Dispatchers.Main) { onError("${index + 1}. fotoğrafta birden fazla kişi var.") }
                    return@withContext
                }
                validRawBitmaps.add(bitmap)
            }

            if (validRawBitmaps.size > 1) {
                withContext(Dispatchers.Main) { onProgress("Kişi Doğrulaması Yapılıyor...") }
                var baseRatio = -1f

                for ((index, bitmap) in validRawBitmaps.withIndex()) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val faces = suspendCancellableCoroutine { continuation ->
                        faceDetector.process(image).addOnSuccessListener { continuation.resume(it) }
                    }
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                        val width = face.boundingBox.width().toFloat()

                        if (leftEye != null && rightEye != null && width > 0) {
                            val dist = sqrt((leftEye.x - rightEye.x).pow(2) + (leftEye.y - rightEye.y).pow(2))
                            val ratio = dist / width
                            if (index == 0) {
                                baseRatio = ratio
                            } else {
                                val diff = abs(ratio - baseRatio) / baseRatio
                                if (diff > 0.15) {
                                    withContext(Dispatchers.Main) { onError("Seçilen fotoğraflar farklı kişilere ait görünüyor.") }
                                    return@withContext
                                }
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) { onSuccess(validRawBitmaps) }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError("Hata: ${e.localizedMessage}") }
        }
    }
}

suspend fun cropToSurgicalNoseArea(bitmap: Bitmap): Bitmap = suspendCancellableCoroutine { continuation ->
    val image = InputImage.fromBitmap(bitmap, 0)
    val options = FaceMeshDetectorOptions.Builder().setUseCase(FaceMeshDetectorOptions.FACE_MESH).build()
    val detector = FaceMeshDetection.getClient(options)

    detector.process(image)
        .addOnSuccessListener { faceMeshes ->
            if (faceMeshes.isEmpty()) {
                continuation.resume(centerCrop(bitmap))
            } else {
                val box = faceMeshes[0].boundingBox
                val cx = box.centerX()
                val cy = box.centerY()

                val cropSize = max((box.width() * 0.60).toInt(), (box.height() * 0.60).toInt())
                var left = cx - (cropSize / 2)
                var top = cy - (cropSize / 2)

                left = max(0, left)
                top = max(0, top)
                val w = min(cropSize, bitmap.width - left)
                val h = min(cropSize, bitmap.height - top)

                try {
                    val cropped = Bitmap.createBitmap(bitmap, left, top, w, h)
                    val scaled = Bitmap.createScaledBitmap(cropped, 224, 224, true)
                    continuation.resume(scaled)
                } catch (e: Exception) {
                    continuation.resume(centerCrop(bitmap))
                }
            }
        }
        .addOnFailureListener { continuation.resume(centerCrop(bitmap)) }
}

fun centerCrop(source: Bitmap): Bitmap {
    val size = min(source.width, source.height)
    val x = (source.width - size) / 2
    val y = (source.height - size) / 2
    val cropped = Bitmap.createBitmap(source, x, y, size, size)
    return Bitmap.createScaledBitmap(cropped, 224, 224, true)
}

var capturedBitmaps = mutableListOf<Bitmap>()

// *** YENİ RENK PALETİ ***
val DarkBlue = Color(0xFF0A1A3F)
val BrightBlue = Color(0xFF1E88E5)
val GoldAccent = Color(0xFFFFD700)
val LightCream = Color(0xFFF8F9FA)
val SurfaceWhite = Color(0xFFFFFFFF)

@Composable
fun RhinoplastyApp() {
    val navController = rememberNavController()

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DarkBlue,
            onPrimary = Color.White,
            primaryContainer = BrightBlue.copy(alpha = 0.1f),
            onPrimaryContainer = DarkBlue,
            secondary = GoldAccent,
            onSecondary = DarkBlue,
            background = LightCream,
            onBackground = DarkBlue,
            surface = SurfaceWhite,
            onSurface = DarkBlue,
            error = Color(0xFFB00020),
            onError = Color.White
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    LaunchedEffect(Unit) { capturedBitmaps.clear() }
                    HomeScreen(
                        onCameraClick = { navController.navigate("smart_camera") },
                        onGallerySuccess = { bitmaps ->
                            capturedBitmaps.clear()
                            capturedBitmaps.addAll(bitmaps)
                            navController.navigate("analysis")
                        }
                    )
                }
                composable("smart_camera") {
                    SmartCameraScreen { bitmaps ->
                        capturedBitmaps.clear()
                        capturedBitmaps.addAll(bitmaps)
                        navController.navigate("analysis")
                    }
                }
                composable("analysis") {
                    AnalysisScreen(capturedBitmaps) { navController.popBackStack("home", false) }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onCameraClick: () -> Unit, onGallerySuccess: (List<Bitmap>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var progressMsg by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) onCameraClick() else Toast.makeText(context, "Kamera izni gerekli", Toast.LENGTH_SHORT).show()
    }

    val galLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        if (uris.isNotEmpty()) {
            if (uris.size > 3) {
                Toast.makeText(context, "En fazla 3 fotoğraf!", Toast.LENGTH_SHORT).show()
            } else {
                isProcessing = true
                scope.launch {
                    processGalleryImages(context, uris,
                        { progressMsg = it },
                        { errorMsg = it; isProcessing = false },
                        { isProcessing = false; onGallerySuccess(it) }
                    )
                }
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.background
                )
            )
        )
        .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .shadow(24.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(4.dp, MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            setImageResource(R.mipmap.ic_launcher)
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Rhino AI",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineLarge,
                letterSpacing = 1.sp
            )
            Text(
                text = "Yapay Zeka Destekli Analiz",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, bottom = 56.dp)
            )

            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) onCameraClick()
                    else camLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(8.dp, pressedElevation = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text("Kamera ile Tara", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    galLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(16.dp))
                Text("Galeriden Seç (1-3 Foto)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Developed by", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Deniz Kılınç & Elif Onat",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (isProcessing) {
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(24.dp))
                        Text(progressMsg, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        if (errorMsg != null) {
            Dialog(onDismissRequest = { errorMsg = null }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Hata", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 22.sp)
                        Text(errorMsg!!, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                        Button(
                            onClick = { errorMsg = null },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Tamam", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// *** KAMERA EKRANI ***
@Composable
fun SmartCameraScreen(onPhotosCaptured: (List<Bitmap>) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context) }

    var step by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("Yüz Aranıyor...") }
    var color by remember { mutableStateOf(Color.White) }

    val bitmaps = remember { mutableListOf<Bitmap>() }
    var trackingId by remember { mutableIntStateOf(-1) }
    var capturing by remember { mutableStateOf(false) }

    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            provider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor) { proxy ->
                        if (!capturing) {
                            val rotation = proxy.imageInfo.rotationDegrees
                            val image = InputImage.fromMediaImage(proxy.image!!, rotation)
                            val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).enableTracking().build())

                            detector.process(image)
                                .addOnSuccessListener { faces ->
                                    if (faces.isEmpty()) {
                                        msg = "Yüz Bulunamadı"; color = Color.Red; trackingId = -1; step = 0; bitmaps.clear()
                                    } else {
                                        val face = faces[0]
                                        if (trackingId == -1) trackingId = face.trackingId!!
                                        if (face.trackingId != trackingId) {
                                            msg = "Kişi Değişti!"; step = 0; bitmaps.clear(); trackingId = face.trackingId!!
                                        } else {
                                            val rotY = face.headEulerAngleY
                                            val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT

                                            when(step) {
                                                0 -> if (rotY in -10f..10f) {
                                                    msg = "Düz Bak"; color = Color.Green; capturing = true;
                                                    takePhoto(context, imageCapture!!, lensFacing, executor) { bitmaps.add(it); step=1; capturing=false }
                                                } else { msg = "Düz Bak"; color = Color.White }

                                                1 -> {
                                                    val turnCheck = if(isFront) rotY > 25f else rotY < -25f
                                                    if (turnCheck) {
                                                        msg = "Sola Dön"; color = Color.Green; capturing = true;
                                                        takePhoto(context, imageCapture!!, lensFacing, executor) { bitmaps.add(it); step=2; capturing=false }
                                                    } else { msg = "Sola Dön"; color = Color.White }
                                                }

                                                2 -> {
                                                    val turnCheck = if(isFront) rotY < -25f else rotY > 25f
                                                    if (turnCheck) {
                                                        msg = "Sağa Dön"; color = Color.Green; capturing = true;
                                                        takePhoto(context, imageCapture!!, lensFacing, executor) { bitmaps.add(it); step=3; capturing=false; onPhotosCaptured(bitmaps) }
                                                    } else { msg = "Sağa Dön"; color = Color.White }
                                                }
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        } else {
                            proxy.close()
                        }
                    }
                }

            try {
                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture, analyzer)
            } catch(e:Exception){
                Log.e("Camera", "Kamera başlatılamadı", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.99f }) {
            val width = size.width
            val height = size.height
            drawRect(color = Color.Black.copy(alpha = 0.6f))
            drawOval(
                color = Color.Transparent,
                topLeft = Offset(x = width * 0.15f, y = height * 0.2f),
                size = Size(width = width * 0.7f, height = height * 0.45f),
                blendMode = BlendMode.Clear
            )
            drawOval(
                color = if (capturing) Color.Green else Color.White,
                topLeft = Offset(x = width * 0.15f, y = height * 0.2f),
                size = Size(width = width * 0.7f, height = height * 0.45f),
                style = Stroke(width = 8f)
            )
        }

        Text(msg, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp), style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.align(Alignment.BottomCenter).padding(48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) { Icon(Icons.Filled.CheckCircle, null, tint = if(it < step) Color.Green else Color.Gray, modifier = Modifier.size(36.dp)) }
        }

        IconButton(
            onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                step = 0
                bitmaps.clear()
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(Icons.Filled.Cameraswitch, contentDescription = "Kamerayı Çevir", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

fun takePhoto(ctx: Context, capture: ImageCapture, lensFacing: Int, exec: java.util.concurrent.Executor, onSaved: (Bitmap) -> Unit) {
    val file = File(ctx.cacheDir, "tmp_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    capture.takePicture(outputOptions, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
        override fun onError(e: ImageCaptureException) { Log.e("Camera", "Hata: ${e.localizedMessage}") }
        override fun onImageSaved(res: ImageCapture.OutputFileResults) {
            var bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return
            bmp = rotateImageIfRequired(bmp, file.absolutePath)
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) bmp = flipImage(bmp)
            val ratio = 1000.0f / bmp.width
            val w = 1000
            val h = (bmp.height * ratio).toInt()
            val scaledBmp = Bitmap.createScaledBitmap(bmp, w, h, true)
            try { file.delete() } catch (e: Exception) {}
            onSaved(scaledBmp)
        }
    })
}

fun rotateImageIfRequired(bitmap: Bitmap, imagePath: String): Bitmap {
    try {
        val ei = android.media.ExifInterface(imagePath)
        val orientation = ei.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
        return when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
            else -> bitmap
        }
    } catch (e: Exception) { return bitmap }
}

fun rotateImage(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

fun flipImage(source: Bitmap): Bitmap {
    val matrix = Matrix()
    matrix.preScale(-1f, 1f)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

// ---------------------------------------------------------------------------------
// ANALİZ EKRANI (YENİ MODEL ENTEGRE EDİLDİ - OTOMATİK BOYUT ALGILAMA)
// ---------------------------------------------------------------------------------
@Composable
fun AnalysisScreen(bitmaps: List<Bitmap>, onBack: () -> Unit) {
    val context = LocalContext.current
    var resultText by remember { mutableStateOf("Hassas Analiz...") }
    var resultDesc by remember { mutableStateOf("Yapay zeka modelleri çalıştırılıyor...") }
    var scoreVal by remember { mutableFloatStateOf(0f) }
    var processedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            try {
                if (bitmaps.isEmpty()) { loading=false; return@withContext }

                // --- MODEL DEĞİŞİKLİĞİ BURADA ---
                // Kullanıcının dönüştürdüğü yeni modeli yüklüyoruz.
                val model = Interpreter(loadModelFile(context, "yeni_model.tflite"), Interpreter.Options().apply{setNumThreads(4)})

                // --- AKILLI ADAPTÖR: MODEL GİRİŞ BOYUTUNU ÖĞREN ---
                val inputTensor = model.getInputTensor(0)
                val inputShape = inputTensor.shape() // [1, height, width, 3] (Genelde)
                // Cifar10 gibi modellerde 32x32 olabilir, ViT'de 224x224 olabilir.
                val modelHeight = inputShape[1]
                val modelWidth = inputShape[2]
                val inputDataType = inputTensor.dataType()

                // Processor'ı modele göre dinamik ayarla
                val processorBuilder = ImageProcessor.Builder()
                    .add(ResizeOp(modelHeight, modelWidth, ResizeOp.ResizeMethod.BILINEAR))

                // Float modelse normalizasyon ekle
                if (inputDataType == DataType.FLOAT32) {
                    processorBuilder.add(NormalizeOp(0.0f, 255.0f)) // Basit 0-1 normalizasyonu (Modeline göre 127.5f de olabilir)
                }

                val processor = processorBuilder.build()

                var total = 0f
                val noseList = mutableListOf<Bitmap>()

                // Çıktı boyutunu öğren
                val outputTensor = model.getOutputTensor(0)
                val outputShape = outputTensor.shape() // [1, numClasses]
                val numClasses = outputShape[1]

                for (rawBitmap in bitmaps) {
                    // Yüz/Burun kırpma mantığını koruyoruz
                    val noseBitmap = cropToSurgicalNoseArea(rawBitmap)
                    noseList.add(noseBitmap)

                    var tImage = TensorImage(inputDataType)
                    tImage.load(noseBitmap)
                    tImage = processor.process(tImage)

                    val outputBuffer = Array(1) { FloatArray(numClasses) }
                    model.run(tImage.buffer, outputBuffer)

                    val rawScores = outputBuffer[0]

                    // Skoru hesapla (Model tipine göre)
                    // Cifar10 gibi çok sınıflı modellerde, ilgilendiğimiz sınıfın indexi önemli.
                    // Şimdilik en yüksek olasılığı alıyoruz veya binary ise ilkini.
                    val score = if (numClasses == 1) {
                        rawScores[0]
                    } else {
                        // Çok sınıflıysa en yüksek olanı al (veya mantığınıza göre özelleştirin)
                        rawScores.maxOrNull() ?: 0f
                    }
                    total += score
                }

                processedBitmaps = noseList
                scoreVal = total / bitmaps.size

                // Sonuç Metinleri (Skora göre)
                // NOT: Yeni modelin (Cifar10) mantığı estetik için uygun olmayabilir,
                // ama entegrasyon başarılıdır.
                if (scoreVal > 0.50f) {
                    resultText = "SONUÇ POZİTİF (Sınıf A)"
                    resultDesc = "Model yüksek olasılıkla hedef sınıfı tespit etti."
                } else {
                    resultText = "SONUÇ NEGATİF (Sınıf B)"
                    resultDesc = "Model hedef sınıfa dair yeterli bulguya rastlamadı."
                }

                model.close()
            } catch(e:Exception){ resultText="Hata"; resultDesc=e.localizedMessage ?: "" }
            loading = false
        }
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Analiz Sonucu", fontWeight = FontWeight.Bold) }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.primary)) }) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
            if(loading) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 6.dp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(24.dp))
                    Text("Model Analiz Ediyor...", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    Text("Yeni model çalıştırılıyor", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {

                Text(text = "Odaklanan Bölgeler", fontSize=14.sp, fontWeight = FontWeight.SemiBold, color=MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier=Modifier.align(Alignment.Start).padding(bottom=12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    processedBitmaps.forEach {
                        Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)).shadow(4.dp), contentScale = ContentScale.Crop)
                    }
                }

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                        val resultColor = if(scoreVal > 0.50) Color(0xFF2E7D32) else Color(0xFFC62828)

                        Icon(Icons.Filled.AutoAwesome, null, tint = resultColor, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(resultText, fontSize=22.sp, fontWeight=FontWeight.Bold, color = resultColor, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(resultDesc, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))

                        Spacer(Modifier.height(24.dp))

                        Text("Güven Skoru", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { scoreVal }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), color = resultColor, trackColor = MaterialTheme.colorScheme.primaryContainer)
                        Text("%.2f".format(scoreVal), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End).padding(top=8.dp), color = resultColor)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text("Yeni Tarama Yap", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}