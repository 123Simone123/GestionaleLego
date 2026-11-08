package com.brickarchivio.app.ui.detail

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.brickarchivio.app.BrickApp
import com.brickarchivio.app.domain.LegoItem
import com.brickarchivio.app.domain.UserPhoto
import com.brickarchivio.app.ui.Thumb
import com.brickarchivio.app.ui.formatPrice
import com.brickarchivio.app.ui.imageModel
import com.brickarchivio.app.ui.openUrl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DetailViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {
    private val repo = (application as BrickApp).repository
    val itemId: Long = checkNotNull(handle["id"])

    val item: StateFlow<LegoItem?> = repo.observeItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(quantity: Int, price: Double?, date: String?, place: String?, notes: String?, done: () -> Unit) =
        viewModelScope.launch {
            repo.updatePersonal(itemId, quantity, price, date, place, notes)
            done()
        }

    fun addTag(name: String) = viewModelScope.launch { repo.addTag(itemId, name) }
    fun removeTag(name: String) = viewModelScope.launch { repo.removeTag(itemId, name) }
    fun addPhoto(uri: Uri) = viewModelScope.launch { repo.addPhoto(itemId, uri) }
    fun deletePhoto(id: Long) = viewModelScope.launch { repo.deletePhoto(id) }
    fun delete(done: () -> Unit) = viewModelScope.launch { repo.deleteItem(itemId); done() }
    fun refresh(done: (String?) -> Unit) = viewModelScope.launch {
        val msg = try { repo.refreshOfficialData(itemId) } catch (e: Exception) { e.message }
        done(msg)
    }
}

private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
private val itDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(onBack: () -> Unit, vm: DetailViewModel = viewModel()) {
    val item by vm.item.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<UserPhoto?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addPhoto(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.type?.label ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                },
                actions = {
                    IconButton(onClick = {
                        vm.refresh { msg -> scope.launch { snackbar.showSnackbar(msg ?: "Dati aggiornati") } }
                    }) { Icon(Icons.Default.Refresh, "Aggiorna dati online") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Elimina") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val it = item ?: return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Immagine ufficiale
            val official = imageModel(it.officialImage.localPath, it.officialImage.url)
            if (official != null) {
                AsyncImage(
                    model = official,
                    contentDescription = it.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            }
            Text(it.name, style = MaterialTheme.typography.headlineSmall)
            OfficialInfo(it)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                it.links.rebrickable?.let { url -> OutlinedButton(onClick = { openUrl(context, url) }) { Text("Rebrickable") } }
                it.links.bricklink?.let { url -> OutlinedButton(onClick = { openUrl(context, url) }) { Text("BrickLink") } }
            }

            HorizontalDivider()
            Text("Le tue foto", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(it.photos, key = { p -> p.id }) { p ->
                    Thumb(File(p.path), 96.dp, Modifier.clickable { viewer = p })
                }
                item {
                    OutlinedButton(onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("+ Foto") }
                }
            }

            HorizontalDivider()
            PersonalEditor(it, vm) { scope.launch { snackbar.showSnackbar("Salvato") } }

            HorizontalDivider()
            Text("Tag", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                it.personal.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { vm.removeTag(tag) },
                        label = { Text("#$tag") },
                        trailingIcon = { Icon(Icons.Default.Close, "Rimuovi") }
                    )
                }
            }
            var newTag by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTag, onValueChange = { v -> newTag = v },
                    singleLine = true, placeholder = { Text("Nuovo tag") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.addTag(newTag); newTag = "" }, enabled = newTag.isNotBlank()) { Text("Aggiungi") }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare?") },
            text = { Text("L'oggetto e le sue foto verranno cancellati dal telefono.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
        )
    }

    viewer?.let { photo ->
        Dialog(onDismissRequest = { viewer = null }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(model = File(photo.path), contentDescription = null, modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(onClick = { vm.deletePhoto(photo.id); viewer = null }) { Text("Elimina foto") }
                    TextButton(onClick = { viewer = null }) { Text("Chiudi") }
                }
            }
        }
    }
}

@Composable
private fun OfficialInfo(item: LegoItem) {
    val rows = buildList {
        add("Codice" to item.code)
        when (item) {
            is LegoItem.LegoSet -> {
                item.theme?.let { add("Tema" to it) }
                item.year?.let { add("Anno" to it.toString()) }
                item.numParts?.let { add("Pezzi" to it.toString()) }
            }
            is LegoItem.Minifigure -> {
                item.theme?.let { add("Tema" to it) }
                item.year?.let { add("Anno" to it.toString()) }
                item.numParts?.let { add("Pezzi" to it.toString()) }
            }
            is LegoItem.Part -> item.category?.let { add("Categoria" to it) }
        }
    }
    Column {
        rows.forEach { (k, v) ->
            Row {
                Text(k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                Text(v, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalEditor(item: LegoItem, vm: DetailViewModel, onSaved: () -> Unit) {
    val p = item.personal
    var quantity by rememberSaveable(item.id) { mutableStateOf(p.quantity.toString()) }
    var price by rememberSaveable(item.id) { mutableStateOf(p.pricePaid?.let { "%.2f".format(it).replace('.', ',') } ?: "") }
    var date by rememberSaveable(item.id) { mutableStateOf(p.purchaseDate ?: "") }
    var place by rememberSaveable(item.id) { mutableStateOf(p.purchasePlace ?: "") }
    var notes by rememberSaveable(item.id) { mutableStateOf(p.notes ?: "") }
    var showDate by remember { mutableStateOf(false) }

    // la quantità può cambiare da fuori (riscansione): la riallineo
    LaunchedEffect(p.quantity) { quantity = p.quantity.toString() }

    Text("Dati personali", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = quantity, onValueChange = { v -> quantity = v.filter(Char::isDigit) },
            label = { Text("Quantità") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = price, onValueChange = { v -> price = v.filter { c -> c.isDigit() || c == ',' || c == '.' } },
            label = { Text("Prezzo pagato €") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.4f)
        )
    }
    Box {
        OutlinedTextField(
            value = date.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it, isoDate).format(itDate) }.getOrDefault(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Data d'acquisto") },
            modifier = Modifier.fillMaxWidth()
        )
        // strato cliccabile sopra il campo in sola lettura
        Box(Modifier.matchParentSize().clickable { showDate = true })
    }
    OutlinedTextField(
        value = place, onValueChange = { v -> place = v },
        label = { Text("Dove l'hai comprato") }, singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = notes, onValueChange = { v -> notes = v },
        label = { Text("Note") }, minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
    Text("Totale pagato: ${formatPrice(price.replace(',', '.').toDoubleOrNull())}", style = MaterialTheme.typography.bodySmall)
    Button(
        onClick = {
            vm.save(
                quantity = quantity.toIntOrNull() ?: 1,
                price = price.replace(',', '.').toDoubleOrNull(),
                date = date.ifBlank { null },
                place = place,
                notes = notes,
                done = onSaved
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Salva dati personali") }

    if (showDate) {
        val initial = runCatching {
            LocalDate.parse(date, isoDate).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    date = pickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(isoDate)
                    } ?: ""
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { date = ""; showDate = false }) { Text("Cancella data") }
            }
        ) { DatePicker(state = pickerState) }
    }
}
