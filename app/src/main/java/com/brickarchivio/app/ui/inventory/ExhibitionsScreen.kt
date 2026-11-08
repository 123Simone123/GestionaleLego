package com.brickarchivio.app.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brickarchivio.app.R
import com.brickarchivio.app.data.db.ExhibitionSummary
import com.brickarchivio.app.ui.AppIcon
import com.brickarchivio.app.ui.BrickColors
import com.brickarchivio.app.ui.DateField
import com.brickarchivio.app.ui.EmptyState
import com.brickarchivio.app.ui.Pill
import com.brickarchivio.app.ui.app
import com.brickarchivio.app.ui.formatDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExhibitionsScreen(onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = context.app.inventory
    val list by remember { repo.observeExhibitionSummaries() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Esposizioni") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nuova esposizione") }
            )
        }
    ) { padding ->
        val l = list
        when {
            l == null -> Unit
            l.isEmpty() -> EmptyState(
                R.drawable.ic_flag, "Nessuna esposizione",
                "Crea un'esposizione, scegli i contenitori che porti e spunta cosa parte e cosa torna.",
                Modifier.padding(padding)
            )
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(l, key = { it.exhibition.id }) { s -> ExhibitionCard(s) { onOpen(s.exhibition.id) } }
            }
        }
    }

    if (creating) {
        ExhibitionDialog(
            title = "Nuova esposizione",
            initialName = "", initialPlace = "", initialStart = null, initialEnd = null, initialNotes = null,
            onDismiss = { creating = false },
            onConfirm = { name, place, start, end, _ ->
                creating = false
                scope.launch { onOpen(repo.createExhibition(name, place, start, end)) }
            }
        )
    }
}

/** Stato sintetico di un'esposizione. */
@Composable
fun StatusPill(entryCount: Int, outbound: Int, returned: Int, missing: Int) {
    when {
        entryCount == 0 -> Pill("Nessun contenitore", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        missing > 0 && returned > 0 -> Pill("$missing mancanti al ritorno", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        outbound > 0 && returned == outbound && missing == 0 -> Pill("Tutto tornato ✓", Color(0xFFD7F0D9), BrickColors.Green)
        outbound < entryCount -> Pill("Preparazione: $outbound/$entryCount", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        else -> Pill("In esposizione", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
private fun ExhibitionCard(s: ExhibitionSummary, onClick: () -> Unit) {
    val e = s.exhibition
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    AppIcon(R.drawable.ic_flag, null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(e.place, dateRange(e.startDate, e.endDate)).joinToString(" · ").ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            StatusPill(s.entryCount, s.outboundCount, s.returnedCount, s.missingCount)
            if (s.entryCount > 0) {
                Spacer(Modifier.height(10.dp))
                ProgressLine("Andata", s.outboundCount, s.entryCount)
                ProgressLine("Ritorno", s.returnedCount, s.entryCount)
            }
        }
    }
}

@Composable
fun ProgressLine(label: String, done: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.weight(1f).height(6.dp)
        )
        Text("$done/$total", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

fun dateRange(start: String?, end: String?): String? {
    val s = formatDate(start)
    val e = formatDate(end)
    return when {
        s != null && e != null && s != e -> "$s – $e"
        s != null -> s
        e != null -> e
        else -> null
    }
}

/** Dialogo crea/modifica esposizione. */
@Composable
fun ExhibitionDialog(
    title: String,
    initialName: String,
    initialPlace: String,
    initialStart: String?,
    initialEnd: String?,
    initialNotes: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var place by remember { mutableStateOf(initialPlace) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var notes by remember { mutableStateOf(initialNotes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome (es. Mostra Lucca 2026)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(place, { place = it }, label = { Text("Luogo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Dal", start, { start = it }, Modifier.weight(1f))
                    DateField("Al", end, { end = it }, Modifier.weight(1f))
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Note") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, place, start, end, notes) }, enabled = name.isNotBlank()) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
