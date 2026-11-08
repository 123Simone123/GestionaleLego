package com.brickarchivio.app.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brickarchivio.app.R
import com.brickarchivio.app.domain.ContainerEntry
import com.brickarchivio.app.domain.LegoItem
import com.brickarchivio.app.ui.AppIcon
import com.brickarchivio.app.ui.EmptyState
import com.brickarchivio.app.ui.Pill
import com.brickarchivio.app.ui.QuantityStepper
import com.brickarchivio.app.ui.Thumb
import com.brickarchivio.app.ui.app
import com.brickarchivio.app.ui.imageModel
import com.brickarchivio.app.ui.mainImage
import kotlinx.coroutines.launch

/** Serve solo a leggere l'id dalla navigazione. */
class IdViewModel(handle: SavedStateHandle) : ViewModel() {
    val id: Long = checkNotNull(handle["id"])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerDetailScreen(onBack: () -> Unit, onOpenItem: (Long) -> Unit, ids: IdViewModel = viewModel()) {
    val context = LocalContext.current
    val repo = context.app.inventory
    val containerId = ids.id
    val container by remember(containerId) { repo.observeContainer(containerId) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var addingManual by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<ContainerEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(container?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Modifica") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Elimina") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                        AppIcon(R.drawable.ic_brick, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Dal catalogo")
                    }
                    FilledTonalButton(onClick = { addingManual = true }, modifier = Modifier.weight(1f)) {
                        AppIcon(R.drawable.ic_pencil, null)
                        Spacer(Modifier.width(8.dp))
                        Text("A mano")
                    }
                }
            }
        }
    ) { padding ->
        val c = container ?: return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("${c.entries.size} righe", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                Pill("${c.totalQuantity} esemplari", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            }
            c.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (c.entries.isEmpty()) {
                EmptyState(
                    R.drawable.ic_box, "Contenitore vuoto",
                    "Aggiungi set e minifigure dal catalogo, oppure scrivi a mano i pezzi sfusi."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(c.entries, key = { it.id }) { e ->
                        EntryCard(
                            e,
                            onQuantity = { q -> scope.launch { repo.updateEntry(e.id, null, q, e.notes) } },
                            onClick = { editEntry = e },
                            onOpenItem = e.itemId?.let { id -> { onOpenItem(id) } }
                        )
                    }
                }
            }
        }
    }

    val c = container
    if (editing && c != null) {
        ContainerDialog("Modifica contenitore", c.name, c.description.orEmpty(), { editing = false }) { name, desc ->
            editing = false
            scope.launch { repo.updateContainer(c.id, name, desc) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare il contenitore?") },
            text = { Text("Verranno eliminate le sue righe e le relative spunte nelle esposizioni. Gli oggetti del catalogo restano.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { repo.deleteContainer(containerId); onBack() }
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
        )
    }
    if (picking && c != null) {
        CatalogPickerDialog(
            alreadyIn = c.entries.mapNotNull { e -> e.itemId?.let { it to e.quantity } }.toMap(),
            onDismiss = { picking = false },
            onPick = { item -> scope.launch { repo.addCatalogItem(containerId, item, 1) } }
        )
    }
    if (addingManual) {
        EntryDialog(
            title = "Voce scritta a mano",
            initialLabel = "", initialQuantity = 1, initialNotes = "", labelEditable = true,
            onDismiss = { addingManual = false },
            onDelete = null,
            onConfirm = { label, qty, notes ->
                addingManual = false
                scope.launch { repo.addManualEntry(containerId, label, qty, notes) }
            }
        )
    }
    editEntry?.let { e ->
        EntryDialog(
            title = if (e.isFromCatalog) "Modifica riga" else "Modifica voce",
            initialLabel = e.label, initialQuantity = e.quantity, initialNotes = e.notes.orEmpty(),
            labelEditable = !e.isFromCatalog,
            onDismiss = { editEntry = null },
            onDelete = { editEntry = null; scope.launch { repo.deleteEntry(e.id) } },
            onConfirm = { label, qty, notes ->
                editEntry = null
                scope.launch { repo.updateEntry(e.id, if (e.isFromCatalog) null else label, qty, notes) }
            }
        )
    }
}

@Composable
private fun EntryCard(e: ContainerEntry, onQuantity: (Int) -> Unit, onClick: () -> Unit, onOpenItem: (() -> Unit)?) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable(enabled = onOpenItem != null) { onOpenItem?.invoke() }) {
                Thumb(
                    imageModel(e.imagePath, e.imageUrl), 56.dp,
                    placeholder = if (e.isFromCatalog) R.drawable.ic_brick else R.drawable.ic_pencil
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (e.isFromCatalog) listOfNotNull(e.itemCode, e.itemType?.label).joinToString(" · ") else "Scritto a mano",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                e.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            QuantityStepper(e.quantity, onQuantity)
        }
    }
}

/** Crea/modifica una riga: nome (solo voci a mano), quantità, note. */
@Composable
private fun EntryDialog(
    title: String,
    initialLabel: String,
    initialQuantity: Int,
    initialNotes: String,
    labelEditable: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onConfirm: (String, Int, String?) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var qty by remember { mutableStateOf(initialQuantity.toString()) }
    var notes by remember { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (labelEditable) {
                    OutlinedTextField(label, { label = it }, label = { Text("Descrizione (es. Pezzi sfusi grigi)") }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                }
                OutlinedTextField(
                    qty, { v -> qty = v.filter(Char::isDigit) },
                    label = { Text("Quantità") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("Note (facoltative)") }, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Rimuovi dal contenitore", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label, qty.toIntOrNull() ?: 1, notes) },
                enabled = label.isNotBlank()
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

/** Scelta di oggetti dal catalogo (ogni tocco aggiunge 1 esemplare). */
@Composable
private fun CatalogPickerDialog(alreadyIn: Map<Long, Int>, onDismiss: () -> Unit, onPick: (LegoItem) -> Unit) {
    val context = LocalContext.current
    val items by remember { context.app.repository.observeItems() }.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val filtered = items.filter {
        query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi dal catalogo") },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Cerca nome o numero") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Tocca per aggiungere 1 esemplare; tocca di nuovo per aggiungerne altri.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Thumb(item.mainImage(), 44.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            alreadyIn[item.id]?.let {
                                Pill("×$it", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fatto") } }
    )
}
