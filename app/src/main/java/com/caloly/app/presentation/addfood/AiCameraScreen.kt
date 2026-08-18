package com.caloly.app.presentation.addfood

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.caloly.app.presentation.theme.CalolyLavender
import com.caloly.app.presentation.theme.CalolyLavenderWhite
import java.io.File

@Composable
fun AiCameraScreen(
    onClose: () -> Unit,
    onPhotoCaptured: (Uri) -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var maximumZoom by remember { mutableStateOf(1f) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        cameraError = if (granted) null else "Arka kamerayı kullanmak için kamera izni vermelisin."
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(hasCameraPermission, lifecycleOwner) {
        if (!hasCameraPermission) return@DisposableEffect onDispose { }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                cameraProvider.unbindAll()
                if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    error("Arka kamera bulunamadı.")
                }
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                ).also { boundCamera ->
                    maximumZoom = boundCamera.cameraInfo.zoomState.value
                        ?.maxZoomRatio
                        ?.coerceAtMost(5f)
                        ?.coerceAtLeast(1f)
                        ?: 1f
                    zoomRatio = 1f
                    boundCamera.cameraControl.setZoomRatio(1f)
                }
            }.onFailure {
                cameraError = "Arka kamera kullanılamıyor. Fotoğraflarımdan seçebilirsin."
            }
        }, mainExecutor)
        onDispose {
            camera?.cameraControl?.setZoomRatio(1f)
            camera = null
            zoomRatio = 1f
            if (cameraProviderFuture.isDone) runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera, maximumZoom) {
                        detectTransformGestures { _, _, gestureZoom, _ ->
                            val nextZoom = (zoomRatio * gestureZoom).coerceIn(1f, maximumZoom)
                            zoomRatio = nextZoom
                            camera?.cameraControl?.setZoomRatio(nextZoom)
                        }
                    },
            )
        }

        if (hasCameraPermission && cameraError == null && maximumZoom > 1f) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 28.dp, vertical = 126.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .62f)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${"%.1f".format(zoomRatio)}×", color = Color.White, fontWeight = FontWeight.Bold)
                    Slider(
                        value = zoomRatio,
                        onValueChange = { value ->
                            zoomRatio = value
                            camera?.cameraControl?.setZoomRatio(value)
                        },
                        valueRange = 1f..maximumZoom,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 20.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape)) {
                Icon(Icons.Rounded.ArrowBack, "Geri", tint = Color.White)
            }
            Card(
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .5f)),
            ) {
                Text("Yalnızca arka kamera", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontSize = 11.sp)
            }
            Spacer(Modifier.size(48.dp))
        }

        cameraError?.let { message ->
            Card(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE17131F)),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(message, color = Color.White, fontWeight = FontWeight.Bold)
                    if (!hasCameraPermission) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = CalolyLavender),
                        ) { Text("İzni Yeniden İste") }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 30.dp).align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.size(58.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(18.dp)),
                ) {
                    Icon(Icons.Rounded.Collections, "Fotoğraflarım", tint = Color.White)
                }
                Text("Fotoğraflarım", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                        return@IconButton
                    }
                    if (isCapturing || cameraError != null) return@IconButton
                    isCapturing = true
                    val directory = File(context.cacheDir, "meal-camera").apply { mkdirs() }
                    val file = File.createTempFile("caloly-meal-", ".jpg", directory)
                    val options = ImageCapture.OutputFileOptions.Builder(file).build()
                    imageCapture.takePicture(
                        options,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                isCapturing = false
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                onPhotoCaptured(uri)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                                cameraError = "Fotoğraf çekilemedi. Tekrar deneyebilirsin."
                            }
                        },
                    )
                },
                modifier = Modifier.size(82.dp).background(Color.White, CircleShape),
            ) {
                Icon(Icons.Rounded.PhotoCamera, "Fotoğraf çek", tint = CalolyLavender, modifier = Modifier.size(38.dp))
            }

            Spacer(Modifier.size(58.dp))
        }

        if (!hasCameraPermission && cameraError == null) {
            Text(
                "Kamera izni bekleniyor…",
                modifier = Modifier.align(Alignment.Center),
                color = CalolyLavenderWhite,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
