package com.cartefedelta.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.cartefedelta.app.barcode.CodeFormat
import com.cartefedelta.app.data.AppDatabase
import com.cartefedelta.app.data.Card
import com.cartefedelta.app.data.Photos
import com.cartefedelta.app.data.Stores
import com.cartefedelta.app.scan.ScannerScreen
import kotlinx.coroutines.launch
import java.io.File

/**
 * Creazione (cardId < 0) o modifica di una carta.
 * Per una nuova carta si apre subito lo scanner, come in Stocard.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditCardScreen(
    cardId: Long,
    storeKey: String?,
    initialName: String,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).cards() }
    val scope = rememberCoroutineScope()
    val isNew = cardId < 0
    val store = Stores.byKey(storeKey)

    var loaded by remember { mutableStateOf<Card?>(null) }
    var ready by rememberSaveable { mutableStateOf(isNew) }
    var name by rememberSaveable { mutableStateOf(store?.name ?: initialName) }
    var number by rememberSaveable { mutableStateOf("") }
    var displayNumber by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(CodeFormat.CODE_128.name) }
    var formatChosen by rememberSaveable { mutableStateOf(false) }
    var color by rememberSaveable { mutableStateOf((store?.color ?: Stores.palette[8]).toInt()) }
    var note by rememberSaveable { mutableStateOf("") }
    var front by rememberSaveable { mutableStateOf<String?>(null) }
    var back by rememberSaveable { mutableStateOf<String?>(null) }
    var scanning by rememberSaveable { mutableStateOf(isNew) }
    var error by remember { mutableStateOf<String?>(null) }
    var photoTarget by remember { mutableStateOf<String?>(null) } // "front" / "back"
    // Foto aggiunte in questa sessione: se si annulla vanno cancellate.
    val newPhotos = remember { mutableListOf<String>() }

    LaunchedEffect(cardId) {
        if (!isNew && !ready) {
            dao.get(cardId)?.let { c ->
                loaded = c
                name = c.name; number = c.number; displayNumber = c.displayNumber
                format = c.format; formatChosen = true; color = c.color; note = c.note
                front = c.frontPhoto; back = c.backPhoto
            }
            ready = true
        } else if (!isNew) {
            loaded = dao.get(cardId)
        }
    }

    // --- Foto ---
    var pendingCapture by remember { mutableStateOf<File?>(null) }
    fun setPhoto(target: String, file: String?) {
        if (file == null) {
            error = "Impossibile salvare la foto"
            return
        }
        newPhotos += file
        if (target == "front") front = file else back = file
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCapture
        val t = photoTarget
        if (ok && f != null && t != null) setPhoto(t, Photos.importFile(context, f))
        pendingCapture = null
    }
    fun launchCamera() {
        val (f, uri) = Photos.newCaptureTarget(context)
        pendingCapture = f
        try {
            takePicture.launch(uri)
        } catch (e: Exception) {
            error = "Nessuna app fotocamera disponibile"
        }
    }
    val camPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else error = "Permesso fotocamera negato"
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val t = photoTarget
        if (uri != null && t != null) setPhoto(t, Photos.importImage(context, uri))
    }
    var photoChoice by remember { mutableStateOf(false) }

    fun cancel() {
        newPhotos.forEach { Photos.delete(context, it) }
        onBack()
    }

    if (scanning) {
        BackHandler { scanning = false }
        ScannerScreen(
            onResult = { r ->
                number = r.value
                format = r.format.name
                formatChosen = true
                scanning = false
            },
            onClose = { scanning = false }
        )
        return
    }

    BackHandler { cancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Nuova carta" else "Modifica carta") },
                navigationIcon = { IconButton(onClick = { cancel() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    TextButton(onClick = {
                        val n = number.trim()
                        when {
                            name.isBlank() -> error = "Scrivi il nome della carta"
                            n.isEmpty() -> error = "Inserisci o scansiona il numero della carta"
                            else -> scope.launch {
                                val fmt = if (formatChosen) format else CodeFormat.guess(n).name
                                val base = loaded ?: Card(name = "", number = "", format = fmt, color = color)
                                val card = base.copy(
                                    name = name.trim(), number = n, format = fmt, color = color,
                                    storeKey = if (isNew) store?.key else base.storeKey,
                                    note = note.trim(), displayNumber = displayNumber.trim(),
                                    frontPhoto = front, backPhoto = back
                                )
                                val id = if (isNew) dao.insert(card) else { dao.update(card); card.id }
                                // Elimina le foto sostituite o tolte
                                loaded?.let { old ->
                                    if (old.frontPhoto != front) Photos.delete(context, old.frontPhoto)
                                    if (old.backPhoto != back) Photos.delete(context, old.backPhoto)
                                }
                                newPhotos.filter { it != front && it != back }.forEach { Photos.delete(context, it) }
                                onSaved(id)
                            }
                        }
                    }) { Text("Salva") }
                }
            )
        }
    ) { padding ->
        if (!ready) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardFace(name.ifBlank { "Nome carta" }, color, front, favorite = false, big = true)

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Numero / contenuto del codice") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (number.isEmpty()) "Scansiona il codice della tessera" else "Scansiona di nuovo")
            }

            FormatPicker(
                selected = if (formatChosen) CodeFormat.of(format) else CodeFormat.guess(number),
                auto = !formatChosen,
                onSelect = { format = it.name; formatChosen = true }
            )

            // Anteprima del codice
            if (number.isNotBlank()) {
                val enc = rememberEncoded(number.trim(), if (formatChosen) CodeFormat.of(format) else CodeFormat.guess(number.trim()))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (enc != null) BarcodeCanvas(enc) else Text("Codice non valido", color = Color.Red)
                }
                if (enc?.fellBack == true) {
                    Text(
                        "Il numero non è valido per il formato scelto: verrà mostrato come Code 128.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            OutlinedTextField(
                value = displayNumber, onValueChange = { displayNumber = it },
                label = { Text("Numero da mostrare (facoltativo)") },
                supportingText = { Text("Se il numero stampato sulla tessera è diverso dal contenuto del codice") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Colore")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val colors = (listOfNotNull(store?.color) + Stores.palette).distinct()
                colors.forEach { c ->
                    val ci = c.toInt()
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(ci))
                            .border(if (ci == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = ci },
                        contentAlignment = Alignment.Center
                    ) {
                        if (ci == color) Icon(Icons.Filled.Check, null, tint = contentColorFor(ci))
                    }
                }
            }

            SectionTitle("Foto della tessera (facoltative)")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoSlot("Fronte", front, Modifier.weight(1f),
                    onSet = { photoTarget = "front"; photoChoice = true },
                    onRemove = { front = null })
                PhotoSlot("Retro", back, Modifier.weight(1f),
                    onSet = { photoTarget = "back"; photoChoice = true },
                    onRemove = { back = null })
            }

            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note") }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { cancel() },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("Annulla") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (photoChoice) {
        AlertDialog(
            onDismissRequest = { photoChoice = false },
            title = { Text("Aggiungi foto") },
            text = {
                Column {
                    TextButton(onClick = {
                        photoChoice = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else camPermission.launch(Manifest.permission.CAMERA)
                    }) { Text("Scatta una foto") }
                    TextButton(onClick = {
                        photoChoice = false
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Scegli dalla galleria") }
                }
            },
            confirmButton = { TextButton(onClick = { photoChoice = false }) { Text("Annulla") } }
        )
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun PhotoSlot(label: String, file: String?, modifier: Modifier, onSet: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onSet),
            contentAlignment = Alignment.Center
        ) {
            if (file != null) {
                AsyncImage(
                    model = Photos.file(context, file), contentDescription = label,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            } else Text("+ $label", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (file != null) {
            TextButton(onClick = onRemove) { Text("Rimuovi") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatPicker(selected: CodeFormat, auto: Boolean, onSelect: (CodeFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected.label + if (auto) " (automatico)" else "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo di codice") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CodeFormat.entries.forEach { f ->
                DropdownMenuItem(text = { Text(f.label) }, onClick = { onSelect(f); open = false })
            }
        }
    }
}
