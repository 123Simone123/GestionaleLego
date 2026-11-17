package com.cartefedelta.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cartefedelta.app.data.AppDatabase
import com.cartefedelta.app.data.Card
import com.cartefedelta.app.scan.CameraPreview
import com.cartefedelta.app.share.ShareCodec
import kotlinx.coroutines.launch

/** Legge i QR di condivisione di un altro telefono e importa le carte. */
@Composable
fun ReceiveScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).cards() }
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) perm.launch(Manifest.permission.CAMERA) }

    val collector = remember { ShareCodec.Collector() }
    var received by remember { mutableIntStateOf(0) }
    var expected by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<List<ShareCodec.SharedCard>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<Set<Pair<String, String>>>(emptySet()) }

    LaunchedEffect(Unit) { existing = dao.all().map { it.name to it.number }.toSet() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted && result == null) {
            CameraPreview(onBarcodes = { list ->
                if (result != null) return@CameraPreview
                var changed = false
                list.forEach { b -> b.rawValue?.let { if (collector.add(it)) changed = true } }
                if (changed) {
                    received = collector.received
                    expected = collector.expected
                    if (collector.isComplete()) {
                        try {
                            result = collector.result()
                        } catch (e: Exception) {
                            error = "Codice non leggibile, riprova."
                        }
                    }
                }
            })
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(0.8f).height(300.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f))
            )
        } else if (!granted) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Serve il permesso della fotocamera per leggere i codici.", color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { perm.launch(Manifest.permission.CAMERA) }) { Text("Consenti") }
            }
        }

        Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "Chiudi", tint = Color.White) }
            Text("Ricevi carte", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(24.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(16.dp)
        ) {
            if (expected > 1) {
                Text("Letti $received codici su $expected", color = Color.White)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { received.toFloat() / expected }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    "Sull'altro telefono apri la carta (o il menu ⋮ → «Condividi tutte») e inquadra il QR che compare.",
                    color = Color.White
                )
            }
        }
    }

    result?.let { cards ->
        val newOnes = cards.filter { (it.name to it.number) !in existing }
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(if (cards.size == 1) "Carta ricevuta" else "${cards.size} carte ricevute") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(cards) { c ->
                        val dup = (c.name to c.number) in existing
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp, 18.dp).clip(RoundedCornerShape(4.dp)).background(Color(c.color)))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(c.name)
                                if (dup) Text("già presente", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = newOnes.isNotEmpty(), onClick = {
                    scope.launch {
                        newOnes.forEach { s ->
                            dao.insert(
                                Card(
                                    name = s.name, number = s.number, format = s.format, color = s.color,
                                    storeKey = s.storeKey, note = s.note, displayNumber = s.displayNumber, favorite = s.favorite
                                )
                            )
                        }
                        onDone()
                    }
                }) { Text(if (newOnes.isEmpty()) "Nessuna nuova" else "Importa ${newOnes.size}") }
            },
            dismissButton = { TextButton(onClick = onDone) { Text("Annulla") } }
        )
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = onDone) { Text("OK") } }
        )
    }
}
