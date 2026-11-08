package com.brickarchivio.app.ui.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import com.brickarchivio.app.data.db.OwnedItem
import com.brickarchivio.app.data.remote.BrickognizeClient
import com.brickarchivio.app.data.remote.Candidate
import com.brickarchivio.app.recognition.Recognizer
import com.brickarchivio.app.domain.ItemType
import com.brickarchivio.app.ui.Thumb
import com.brickarchivio.app.ui.search.ManualSearchPanel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    vm: ScanViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val title = when (state.step) {
        ScanStep.PICK -> "Nuova scansione"
        ScanStep.EDIT_REGIONS -> "Foto ${state.index + 1}/${state.queue.size} · oggetti"
        ScanStep.REVIEW -> "Foto ${state.index + 1}/${state.queue.size} · conferma"
        ScanStep.DONE -> "Fatto"
        ScanStep.BUSY -> "Attendere…"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.step) {
                ScanStep.PICK -> PickStep(state, vm)
                ScanStep.BUSY -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(state.busyMessage)
                }
                ScanStep.EDIT_REGIONS -> EditStep(state, vm)
                ScanStep.REVIEW -> ReviewStep(state, vm)
                ScanStep.DONE -> DoneStep(state, onOpenItem, onRestart = vm::restart, onBack = onBack)
            }
        }
    }
}

@Composable
private fun PickStep(state: ScanState, vm: ScanViewModel) {
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        vm.onCaptured(ok)
    }
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris -> vm.onPicked(uris) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Fotografa uno o più oggetti (scatole, set montati, minifigure, pezzi). " +
                "Puoi scattare più foto di seguito; poi tocca «Avanti».",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Consiglio: sfondo uniforme, oggetti separati tra loro, numero della scatola ben visibile.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Cosa fotografi?", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BrickognizeClient.Mode.entries.forEach { m ->
                FilterChip(selected = state.mode == m, onClick = { vm.setMode(m) }, label = { Text(m.label) })
            }
        }
        Text(
            "Scegliere «Minifigure», «Set» o «Pezzi» usa un riconoscitore specifico, più preciso di «Tutto».",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { takePicture.launch(vm.newCaptureUri()) }, modifier = Modifier.weight(1f)) {
                Text("Scatta foto")
            }
            OutlinedButton(
                onClick = {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f)
            ) { Text("Dalla galleria") }
        }
        if (state.queue.isNotEmpty()) {
            Text("Foto pronte: ${state.queue.size}", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.queue) { path ->
                    Box {
                        Thumb(File(path), 96.dp)
                        IconButton(onClick = { vm.removeFromQueue(path) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                            Icon(Icons.Default.Close, "Rimuovi")
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = vm::startProcessing, modifier = Modifier.fillMaxWidth()) {
                Text("Avanti (${state.queue.size} foto)")
            }
        }
    }
}

@Composable
private fun EditStep(state: ScanState, vm: ScanViewModel) {
    val bitmap = state.bitmap ?: return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Controlla i riquadri: uno per ogni oggetto. Tocca per selezionare, trascina per spostare, " +
                "trascina gli angoli per ridimensionare.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rilevamento:", style = MaterialTheme.typography.labelMedium)
            Recognizer.DetectMethod.entries.forEach { m ->
                FilterChip(
                    selected = state.detectMethod == m,
                    onClick = { vm.setDetectMethod(m) },
                    label = {
                        Text(
                            if (m == Recognizer.DetectMethod.AUTO && state.detectMethod == m && state.detectUsed != null)
                                "Automatico (${state.detectUsed.label})" else m.label
                        )
                    }
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            RegionEditor(
                image = image,
                regions = state.regions,
                selectedId = state.selectedRegion,
                onSelect = vm::selectRegion,
                onChange = vm::updateRegion
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = vm::addRegion, modifier = Modifier.weight(1f)) { Text("+ Riquadro") }
            OutlinedButton(
                onClick = vm::deleteSelectedRegion,
                enabled = state.selectedRegion != null,
                modifier = Modifier.weight(1f)
            ) { Text("Elimina") }
            OutlinedButton(onClick = vm::useWholePhoto, modifier = Modifier.weight(1f)) { Text("Foto intera") }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = vm::skipPhoto) { Text("Salta foto") }
            Button(
                onClick = vm::recognize,
                enabled = state.regions.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("Riconosci ${state.regions.size} oggett${if (state.regions.size == 1) "o" else "i"}") }
        }
    }
}

@Composable
private fun ReviewStep(state: ScanState, vm: ScanViewModel) {
    var searchFor by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.results, key = { it.regionId }) { r ->
                val number = state.regions.indexOfFirst { it.id == r.regionId } + 1
                ResultCard(number, r, state.owned, onChoose = { vm.choose(r.regionId, it) }, onManual = { searchFor = r.regionId })
            }
        }
        val count = state.results.count { it.selected in it.candidates.indices }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = vm::backToRegions) { Text("Riquadri") }
            TextButton(onClick = vm::skipPhoto) { Text("Salta") }
            Button(onClick = vm::saveCurrent, modifier = Modifier.weight(1f)) {
                Text(if (count == 0) "Avanti senza salvare" else "Salva $count nel catalogo")
            }
        }
    }

    state.duplicates?.let { dups -> DuplicatesDialog(dups, vm) }

    searchFor?.let { regionId ->
        AlertDialog(
            onDismissRequest = { searchFor = null },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { searchFor = null }) { Text("Chiudi") } },
            title = { Text("Cerca a mano") },
            text = {
                ManualSearchPanel(Modifier.heightIn(max = 480.dp)) { candidate ->
                    vm.setManual(regionId, candidate.copy(source = "Ricerca manuale"))
                    searchFor = null
                }
            }
        )
    }
}

@Composable
private fun ResultCard(number: Int, r: ResultUi, owned: Map<String, OwnedItem>, onChoose: (Int) -> Unit, onManual: () -> Unit) {
    var showTech by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(File(r.cropPath), 72.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Oggetto $number", style = MaterialTheme.typography.titleMedium)
                    if (r.numbersRead.isNotEmpty()) {
                        Text("Numeri letti: ${r.numbersRead.take(5).joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (r.candidates.isEmpty()) {
                        Text("Nessun riconoscimento", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            r.candidates.forEachIndexed { i, c -> CandidateRow(c, owned[c.key()], selected = r.selected == i, onClick = { onChoose(i) }) }
            Row(
                Modifier.fillMaxWidth().clickable { onChoose(-1) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = r.selected == -1, onClick = { onChoose(-1) })
                Text("Non aggiungere")
            }
            Row {
                TextButton(onClick = onManual) { Text("Cerca a mano…") }
                if (r.errors.isNotEmpty() || r.raw != null) {
                    TextButton(onClick = { showTech = !showTech }) { Text(if (showTech) "Nascondi dettagli" else "Dettagli tecnici") }
                }
            }
            if (showTech) {
                HorizontalDivider()
                SelectionContainer {
                    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        r.errors.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        r.raw?.let {
                            Text(it.take(4000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(c: Candidate, owned: OwnedItem?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Thumb(c.imageUrl, 52.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    c.type.label,
                    c.code,
                    c.score?.let { "${(it * 100).toInt()}%" },
                    c.source.ifBlank { null }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (c.type == ItemType.SET) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            if (owned != null) {
                Text(
                    "Già in catalogo (×${owned.quantity})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DoneStep(state: ScanState, onOpenItem: (Long) -> Unit, onRestart: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            if (state.saved.isEmpty()) "Nessun oggetto salvato." else "Salvati ${state.saved.count { it.itemId > 0 }} oggetti:",
            style = MaterialTheme.typography.titleMedium
        )
        LazyColumn(Modifier.weight(1f)) {
            items(state.saved) { s ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = s.itemId > 0) { onOpenItem(s.itemId) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(s.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (s.itemId <= 0) "Non salvato" else if (s.created) "Nuovo nel catalogo" else "Già presente: quantità +1",
                        style = MaterialTheme.typography.bodySmall
                    )
                    s.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f)) { Text("Nuova scansione") }
            Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Catalogo") }
        }
    }
}

@Composable
private fun DuplicatesDialog(dups: List<DuplicateUi>, vm: ScanViewModel) {
    AlertDialog(
        onDismissRequest = vm::cancelDuplicates,
        title = { Text("Già presenti") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Per ognuno scegli se aggiungerlo comunque (la quantità aumenta di 1) o saltarlo.")
                dups.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(
                                    d.code,
                                    if (d.ownedQuantity > 0) "in catalogo ×${d.ownedQuantity}" else null,
                                    if (d.repeatedInPhoto) "ripetuto in questa foto" else null
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                if (d.add) "Aggiungi (quantità +1)" else "Salta",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (d.add) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Switch(checked = d.add, onCheckedChange = { vm.setDuplicateChoice(d.regionId, it) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::confirmDuplicates) { Text("Continua") } },
        dismissButton = { TextButton(onClick = vm::cancelDuplicates) { Text("Annulla") } }
    )
}
