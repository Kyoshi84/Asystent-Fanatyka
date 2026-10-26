package pl.asystentfanatyka.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.asystentfanatyka.container
import pl.asystentfanatyka.data.AppLog
import pl.asystentfanatyka.ui.components.BigActionButton
import pl.asystentfanatyka.ui.components.SectionCard
import pl.asystentfanatyka.ui.components.WarningBox
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class PhotoPurpose(val title: String) { RECOGNIZE("Rozpoznaj rybę"), MEASURE("Zmierz rybę") }

/** Wybór źródła zdjęcia: aparat, galeria (Android Photo Picker) lub – przy pomiarze – sam odczyt z maty. */
@Composable
fun PhotoSourceScreen(
    purpose: PhotoPurpose,
    onCamera: () -> Unit,
    onPhotoReady: (String) -> Unit,
    onSkipPhoto: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val f = context.container.photos.importFromUri(context.contentResolver, uri)
                onPhotoReady(f.absolutePath)
            } catch (t: Throwable) {
                AppLog.e("Import zdjęcia z galerii nie powiódł się", t)
                error = "Nie udało się wczytać zdjęcia: ${t.message}"
            } finally {
                busy = false
            }
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (purpose == PhotoPurpose.MEASURE) {
            SectionCard("Jak przygotować pomiar") {
                Text("1. Połóż rybę płasko na macie z podziałką lub obok miarki, na tej samej płaszczyźnie.")
                Text("2. Pysk zamknięty, płetwa ogonowa w naturalnym ułożeniu – mierzy się do końca najdłuższego promienia płetwy ogonowej.")
                Text("3. Zrób zdjęcie prostopadle z góry, tak by ryba i podziałka wypełniały kadr.")
                Text("Najdokładniejszy jest odczyt bezpośrednio z maty – zdjęcie służy do dokumentacji i sprawdzenia.")
            }
        } else {
            SectionCard("Wskazówki do zdjęcia") {
                Text("Cała ryba z boku, w dobrym świetle, bez odblasków. Rozłóż płetwy grzbietową i odbytową – to one często rozstrzygają o gatunku.")
            }
        }
        BigActionButton("Zrób zdjęcie", Icons.Filled.CameraAlt, onCamera)
        BigActionButton("Wybierz z galerii", Icons.Filled.PhotoLibrary, {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, primary = false)
        if (onSkipPhoto != null) {
            BigActionButton(
                if (purpose == PhotoPurpose.MEASURE) "Bez zdjęcia – wpiszę odczyt z maty" else "Bez zdjęcia – wybiorę gatunek",
                Icons.Filled.EditNote, onSkipPhoto, primary = false
            )
        }
        if (busy) CircularProgressIndicator()
        error?.let { WarningBox(it) }
        Text(
            "Zdjęcia są zapisywane tylko w pamięci telefonu, w prywatnym katalogu aplikacji.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Aparat (CameraX). */
@Composable
fun CameraScreen(onCaptured: (String) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        denied = !granted
    }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    if (!hasPermission) {
        Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WarningBox(
                if (denied) "Brak zgody na użycie aparatu. Możesz wybrać zdjęcie z galerii albo nadać uprawnienie w ustawieniach systemu."
                else "Aplikacja potrzebuje dostępu do aparatu, aby zrobić zdjęcie ryby."
            )
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Poproś ponownie") }
            OutlinedButton(onClick = onCancel) { Text("Wróć") }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (t: Throwable) {
                AppLog.e("Nie udało się uruchomić aparatu", t)
                error = "Nie udało się uruchomić aparatu: ${t.message}"
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        error?.let { WarningBox(it, Modifier.align(Alignment.TopCenter).padding(16.dp)) }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onCancel) { Text("Anuluj", color = Color.White) }
            FilledIconButton(
                onClick = {
                    if (capturing) return@FilledIconButton
                    capturing = true
                    val raw = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(raw).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                scope.launch {
                                    try {
                                        val f = context.container.photos.normalize(raw)
                                        raw.delete()
                                        onCaptured(f.absolutePath)
                                    } catch (t: Throwable) {
                                        AppLog.e("Zapis zdjęcia nie powiódł się", t)
                                        error = "Nie udało się zapisać zdjęcia."
                                    } finally {
                                        capturing = false
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                AppLog.e("Błąd aparatu", exception)
                                error = "Błąd aparatu: ${exception.message}"
                                capturing = false
                            }
                        }
                    )
                },
                modifier = Modifier.size(84.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                if (capturing) CircularProgressIndicator(Modifier.size(36.dp)) else Icon(Icons.Filled.CameraAlt, "Zrób zdjęcie", Modifier.size(40.dp))
            }
        }
    }
}

/** Dopasowanie obrazu do pola (ContentScale.Fit): skala i przesunięcie. */
data class FitTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toScreen(x: Float, y: Float) = Offset(offsetX + x * scale, offsetY + y * scale)
    fun toImage(p: Offset) = Offset((p.x - offsetX) / scale, (p.y - offsetY) / scale)

    companion object {
        fun of(containerW: Float, containerH: Float, imageW: Int, imageH: Int): FitTransform {
            val s = min(containerW / imageW, containerH / imageH)
            return FitTransform(s, (containerW - imageW * s) / 2f, (containerH - imageH * s) / 2f)
        }
    }
}

/** Przycinanie zdjęcia prostokątem z uchwytami w narożnikach. */
@Composable
fun CropScreen(photoPath: String, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photoPath) {
        bitmap = withContext(Dispatchers.IO) { PhotoStorage.loadBitmap(photoPath, PhotoStorage.MAX_SIDE) }
    }
    val bmp = bitmap ?: run {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // Prostokąt w pikselach obrazu
    var left by remember(bmp) { mutableStateOf(bmp.width * 0.05f) }
    var top by remember(bmp) { mutableStateOf(bmp.height * 0.05f) }
    var right by remember(bmp) { mutableStateOf(bmp.width * 0.95f) }
    var bottom by remember(bmp) { mutableStateOf(bmp.height * 0.95f) }
    var saving by remember { mutableStateOf(false) }
    val handleRadius = with(LocalDensity.current) { 28.dp.toPx() }

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Przeciągnij narożniki, aby kadr obejmował całą rybę (i podziałkę, jeśli będziesz mierzyć).")
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val cw = constraints.maxWidth.toFloat()
            val ch = constraints.maxHeight.toFloat()
            val t = FitTransform.of(cw, ch, bmp.width, bmp.height)
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Canvas(
                Modifier.fillMaxSize().pointerInput(bmp, t) {
                    var corner = -1
                    detectDragGestures(
                        onDragStart = { pos ->
                            val corners = listOf(t.toScreen(left, top), t.toScreen(right, top), t.toScreen(right, bottom), t.toScreen(left, bottom))
                            corner = corners.indices.minByOrNull { (corners[it] - pos).getDistance() }
                                ?.takeIf { (corners[it] - pos).getDistance() < handleRadius * 2.5f } ?: -1
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val p = t.toImage(change.position)
                            val x = p.x.coerceIn(0f, bmp.width.toFloat())
                            val y = p.y.coerceIn(0f, bmp.height.toFloat())
                            val minSize = 0.1f * max(bmp.width, bmp.height)
                            when (corner) {
                                0 -> { left = min(x, right - minSize); top = min(y, bottom - minSize) }
                                1 -> { right = max(x, left + minSize); top = min(y, bottom - minSize) }
                                2 -> { right = max(x, left + minSize); bottom = max(y, top + minSize) }
                                3 -> { left = min(x, right - minSize); bottom = max(y, top + minSize) }
                            }
                        },
                        onDragEnd = { corner = -1 }
                    )
                }
            ) {
                val tl = t.toScreen(left, top)
                val br = t.toScreen(right, bottom)
                val dim = Color.Black.copy(alpha = 0.5f)
                drawRect(dim, Offset(0f, 0f), Size(size.width, tl.y))
                drawRect(dim, Offset(0f, br.y), Size(size.width, size.height - br.y))
                drawRect(dim, Offset(0f, tl.y), Size(tl.x, br.y - tl.y))
                drawRect(dim, Offset(br.x, tl.y), Size(size.width - br.x, br.y - tl.y))
                drawRect(Color.White, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(3f))
                listOf(tl, Offset(br.x, tl.y), br, Offset(tl.x, br.y)).forEach {
                    drawCircle(Color.White, handleRadius * 0.5f, it)
                    drawCircle(Color(0xFF0E4A5C), handleRadius * 0.5f, it, style = Stroke(4f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onDone(photoPath) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Bez przycinania") }
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val l = left.toInt().coerceIn(0, bmp.width - 1)
                            val tp = top.toInt().coerceIn(0, bmp.height - 1)
                            val w = (right - left).toInt().coerceIn(1, bmp.width - l)
                            val h = (bottom - top).toInt().coerceIn(1, bmp.height - tp)
                            val cropped = Bitmap.createBitmap(bmp, l, tp, w, h)
                            val f = context.container.photos.saveCropped(cropped)
                            if (abs(w - bmp.width) > 1 || abs(h - bmp.height) > 1) File(photoPath).delete()
                            onDone(f.absolutePath)
                        } catch (t: Throwable) {
                            AppLog.e("Przycinanie nie powiodło się", t)
                            onDone(photoPath)
                        } finally {
                            saving = false
                        }
                    }
                },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
            ) {
                Icon(Icons.Filled.Crop, null)
                Text(" Przytnij")
            }
        }
    }
}
