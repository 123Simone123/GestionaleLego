package com.brickarchivio.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brickarchivio.app.R
import com.brickarchivio.app.domain.ChecklistRow
import com.brickarchivio.app.domain.ChecklistSection
import com.brickarchivio.app.domain.Exhibition
import com.brickarchivio.app.ui.SectionTitle
import com.brickarchivio.app.ui.Thumb
import com.brickarchivio.app.ui.app
import com.brickarchivio.app.ui.imageModel
import kotlinx.coroutines.launch

private val CheckColumn = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExhibitionDetailScreen(onBack: () -> Unit, onOpenContainer: (Long) -> Unit, ids: IdViewModel = viewModel()) {
    val context = LocalContext.current
    val repo = context.app.inventory
    val exId = ids.id
    val exhibition by remember(exId) { repo.observeExhibition(exId) }.collectAsState(initial = null)
    val allContainers by remember { repo.observeContainers() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addingContainers by remember { mutableStateOf(false) }
    var onlyMissing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exhibition?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Modifica") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Elimina") }
                }
            )
        }
    ) { padding ->
        val ex = exhibition ?: return@Scaffold
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SummaryCard(ex) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { addingContainers = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Contenitori")
                    }
                    FilterChip(
                        selected = onlyMissing,
                        onClick = { onlyMissing = !onlyMissing },
                        label = { Text("Solo non tornati (${ex.missing.size})") }
                    )
                }
            }
            if (ex.sections.isEmpty()) {
                item {
                    Text(
                        "Aggiungi i contenitori che porti all'esposizione: le loro righe compariranno qui con le colonne Andata e Ritorno.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            items(ex.sections, key = { it.container.id }) { section ->
                SectionCard(
                    section = section,
                    onlyMissing = onlyMissing,
                    highlightMissing = ex.returnedCount > 0,
                    onOutbound = { row, v -> scope.launch { repo.setOutbound(exId, row.entry.id, v) } },
                    onReturned = { row, v -> scope.launch { repo.setReturned(exId, row.entry.id, v) } },
                    onAll = { out, ret -> scope.launch { repo.setAll(exId, section.container.id, out, ret) } },
                    onOpenContainer = { onOpenContainer(section.container.id) },
                    onRemove = { scope.launch { repo.removeContainerFromExhibition(exId, section.container.id) } }
                )
            }
        }
    }

    val ex = exhibition
    if (editing && ex != null) {
        ExhibitionDialog(
            "Modifica esposizione", ex.name, ex.place.orEmpty(), ex.startDate, ex.endDate, ex.notes,
            onDismiss = { editing = false },
            onConfirm = { name, place, start, end, notes ->
                editing = false
                scope.launch { repo.updateExhibition(exId, name, place, start, end, notes) }
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare l'esposizione?") },
            text = { Text("Verranno cancellate le spunte di andata e ritorno. I contenitori restano.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; scope.launch { repo.deleteExhibition(exId); onBack() } }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
        )
    }
    if (addingContainers && ex != null) {
        val included = ex.sections.map { it.container.id }.toSet()
        val available = allContainers.filter { it.id !in included }
        val chosen = remember { mutableStateListOf<Long>() }
        AlertDialog(
            onDismissRequest = { addingContainers = false },
            title = { Text("Aggiungi contenitori") },
            text = {
                if (available.isEmpty()) {
                    Text("Non ci sono altri contenitori. Creali nella scheda «Contenitori».")
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(available, key = { it.id }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { if (c.id in chosen) chosen.remove(c.id) else chosen.add(c.id) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = c.id in chosen, onCheckedChange = { if (it) chosen.add(c.id) else chosen.remove(c.id) })
                                Column {
                                    Text(c.name)
                                    Text("${c.entries.size} righe", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = chosen.toList()
                    addingContainers = false
                    scope.launch { ids.forEach { repo.addContainerToExhibition(exId, it) } }
                }, enabled = chosen.isNotEmpty()) { Text("Aggiungi") }
            },
            dismissButton = { TextButton(onClick = { addingContainers = false }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun SummaryCard(ex: Exhibition) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            val info = listOfNotNull(ex.place, dateRange(ex.startDate, ex.endDate)).joinToString(" · ")
            if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodyMedium)
            ex.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.width(0.dp))
            Box(Modifier.padding(vertical = 8.dp)) {
                StatusPill(ex.total, ex.outboundCount, ex.returnedCount, ex.missing.size)
            }
            ProgressLine("Andata", ex.outboundCount, ex.total)
            ProgressLine("Ritorno", ex.returnedCount, ex.total)
            if (ex.returnedCount > 0 && ex.missing.isNotEmpty()) {
                SectionTitle("Partiti ma non tornati")
                ex.missing.forEach { r ->
                    Text(
                        "• ${r.entry.label} ×${r.entry.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    section: ChecklistSection,
    onlyMissing: Boolean,
    highlightMissing: Boolean,
    onOutbound: (ChecklistRow, Boolean) -> Unit,
    onReturned: (ChecklistRow, Boolean) -> Unit,
    onAll: (Boolean?, Boolean?) -> Unit,
    onOpenContainer: () -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val rows = if (onlyMissing) section.rows.filter { it.missing } else section.rows
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth()) {
            // intestazione contenitore + colonne
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).clickable(onClick = onOpenContainer).padding(vertical = 8.dp)) {
                    Text(section.container.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "${section.rows.size} righe",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                ColumnHeader("Andata\n${section.outboundCount}/${section.rows.size}")
                ColumnHeader("Ritorno\n${section.returnedCount}/${section.rows.size}")
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Azioni") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Spunta tutto: andata") }, onClick = { menu = false; onAll(true, null) })
                        DropdownMenuItem(text = { Text("Spunta tutto: ritorno") }, onClick = { menu = false; onAll(null, true) })
                        DropdownMenuItem(text = { Text("Togli spunte andata") }, onClick = { menu = false; onAll(false, null) })
                        DropdownMenuItem(text = { Text("Togli spunte ritorno") }, onClick = { menu = false; onAll(null, false) })
                        DropdownMenuItem(text = { Text("Apri contenitore") }, onClick = { menu = false; onOpenContainer() })
                        DropdownMenuItem(
                            text = { Text("Togli da questa esposizione", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onRemove() }
                        )
                    }
                }
            }
            if (section.rows.isEmpty()) {
                Text("Contenitore vuoto", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider()
                val missing = highlightMissing && row.missing
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(start = 10.dp, end = 48.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Thumb(
                        imageModel(row.entry.imagePath, row.entry.imageUrl), 40.dp,
                        placeholder = if (row.entry.isFromCatalog) R.drawable.ic_brick else R.drawable.ic_pencil
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.entry.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull("×${row.entry.quantity}", row.entry.itemCode).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(Modifier.width(CheckColumn), contentAlignment = Alignment.Center) {
                        Checkbox(checked = row.outbound, onCheckedChange = { onOutbound(row, it) })
                    }
                    Box(Modifier.width(CheckColumn), contentAlignment = Alignment.Center) {
                        Checkbox(checked = row.returned, onCheckedChange = { onReturned(row, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.width(CheckColumn)
    )
}
