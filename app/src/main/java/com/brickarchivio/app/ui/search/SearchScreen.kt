package com.brickarchivio.app.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.brickarchivio.app.data.db.OwnedItem
import com.brickarchivio.app.data.remote.Candidate
import com.brickarchivio.app.ui.app
import com.brickarchivio.app.ui.scan.key
import kotlinx.coroutines.launch

/** Aggiunta senza foto: cerchi il set per numero/nome e lo aggiungi al catalogo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenItem: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf<Pair<Candidate, OwnedItem>?>(null) }

    fun add(candidate: Candidate) {
        saving = true
        scope.launch {
            try {
                val res = context.app.repository.addFromCandidate(candidate, null, null)
                onOpenItem(res.itemId)
            } catch (e: Exception) {
                snackbar.showSnackbar("Errore: ${e.message}")
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cerca e aggiungi") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            ManualSearchPanel(Modifier.padding(12.dp)) { candidate ->
                if (saving) return@ManualSearchPanel
                scope.launch {
                    val owned = runCatching { context.app.repository.owned()[candidate.key()] }.getOrNull()
                    if (owned != null) duplicate = candidate to owned else add(candidate)
                }
            }
            if (saving) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    duplicate?.let { (candidate, owned) ->
        AlertDialog(
            onDismissRequest = { duplicate = null },
            title = { Text("Già in catalogo") },
            text = { Text("«${owned.name}» è già presente (×${owned.quantity}). Vuoi aggiungerne un altro esemplare?") },
            confirmButton = {
                TextButton(onClick = { duplicate = null; add(candidate) }) { Text("Aumenta quantità") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { duplicate = null; onOpenItem(owned.id) }) { Text("Apri scheda") }
                    TextButton(onClick = { duplicate = null }) { Text("Annulla") }
                }
            }
        )
    }
}
