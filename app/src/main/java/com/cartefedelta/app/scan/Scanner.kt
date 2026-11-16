package com.cartefedelta.app.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cartefedelta.app.barcode.CodeFormat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class ScanResult(val value: String, val format: CodeFormat)

/** Legge un codice da una foto già scattata o dalla galleria. */
suspend fun scanImage(context: Context, uri: Uri): ScanResult? = suspendCancellableCoroutine { cont ->
    try {
        val image = InputImage.fromFilePath(context, uri)
        BarcodeScanning.getClient().process(image)
            .addOnSuccessListener { list ->
                val b = list.firstOrNull { it.rawValue != null && CodeFormat.fromMlKit(it.format) != null }
                cont.resume(b?.let { ScanResult(it.rawValue!!, CodeFormat.fromMlKit(it.format)!!) })
            }
            .addOnFailureListener { cont.resume(null) }
    } catch (e: Exception) {
        cont.resume(null)
    }
}

/** Schermata a tutto schermo con la fotocamera che inquadra il codice. */
@Composable
fun ScannerScreen(onResult: (ScanResult) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }

    val scope = rememberCoroutineScope()
    var galleryMsg by remember { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val r = scanImage(context, uri)
            if (r != null) onResult(r) else galleryMsg = "Nessun codice trovato nella foto"
        }
    }
    var torch by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(onResult = onResult, onCamera = { cameraControl = it })
            // Mirino
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.8f)
                    .height(220.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (asked) "Serve il permesso della fotocamera per leggere il codice." else "Richiesta permesso fotocamera…",
                    color = Color.White
                )
                if (asked) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("Consenti") }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Chiudi", tint = Color.White) }
            Text("Inquadra il codice a barre", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            galleryMsg?.let {
                Text(it, color = Color.White, modifier = Modifier.background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(8.dp))
                Spacer(Modifier.height(12.dp))
            }
            // Pulsanti su due righe: entrano anche negli schermi stretti
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (granted) {
                    FilledTonalButton(
                        onClick = {
                            torch = !torch
                            cameraControl?.enableTorch(torch)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (torch) "Spegni torcia" else "Torcia", maxLines = 1) }
                }
                FilledTonalButton(
                    onClick = {
                        gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Da una foto", maxLines = 1) }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Scrivi il numero a mano", maxLines = 1)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onResult: (ScanResult) -> Unit, onCamera: (androidx.camera.core.CameraControl) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val done = remember { AtomicBoolean(false) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || done.get()) {
                    proxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { list ->
                        val b = list.firstOrNull { it.rawValue != null && CodeFormat.fromMlKit(it.format) != null }
                        if (b != null && done.compareAndSet(false, true)) {
                            onResult(ScanResult(b.rawValue!!, CodeFormat.fromMlKit(b.format)!!))
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                onCamera(camera.cameraControl)
            } catch (e: Exception) {
                // Nessuna fotocamera disponibile: resta la scelta "da una foto" o manuale.
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (e: Exception) {
            }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
