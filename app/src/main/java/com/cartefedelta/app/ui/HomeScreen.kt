package com.cartefedelta.app.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartefedelta.app.data.AppDatabase
import com.cartefedelta.app.data.Card
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    USAGE("Più usate"),
    RECENT("Usate di recente"),
    NAME("Nome A-Z"),
    ADDED("Aggiunte di recente")
}

private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
    onShareAll: () -> Unit,
    onReceive: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).cards() }
    val cards by dao.observeAll().collectAsStateWithLifecycle(initialValue = null)
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sort by remember {
        mutableStateOf(SortMode.entries.firstOrNull { it.name == prefs(context).getString("sort", null) } ?: SortMode.USAGE)
    }
    var sortMenu by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Card?>(null) }
    val scope = rememberCoroutineScope()

    val visible = remember(cards, query, sort) {
        val list = (cards ?: emptyList()).filter {
            query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
                it.note.contains(query.trim(), ignoreCase = true)
        }
        val sorted = when (sort) {
            SortMode.USAGE -> list.sortedWith(compareByDescending<Card> { it.useCount }.thenBy { it.name.lowercase() })
            SortMode.RECENT -> list.sortedWith(compareByDescending<Card> { it.lastUsedAt }.thenBy { it.name.lowercase() })
            SortMode.NAME -> list.sortedBy { it.name.lowercase() }
            SortMode.ADDED -> list.sortedByDescending { it.createdAt }
        }
        sorted.partition { it.favorite }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Cerca una carta") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                        )
                    } else Text("Le mie carte")
                },
                actions = {
                    IconButton(onClick = {
                        if (searching) query = ""
                        searching = !searching
                    }) {
                        Icon(if (searching) Icons.Filled.Clear else Icons.Filled.Search, "Cerca")
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text((if (mode == sort) "✓ " else "    ") + mode.label) },
                                    onClick = {
                                        sort = mode
                                        prefs(context).edit().putString("sort", mode.name).apply()
                                        sortMenu = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Condividi tutte (QR)") }, onClick = { sortMenu = false; onShareAll() })
                            DropdownMenuItem(text = { Text("Ricevi carte (QR)") }, onClick = { sortMenu = false; onReceive() })
                        }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Impostazioni") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Aggiungi carta") }
            )
        }
    ) { padding ->
        val (favs, others) = visible
        if (cards != null && cards!!.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nessuna carta ancora", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tocca «Aggiungi carta», scegli il negozio e inquadra il codice a barre della tessera.\nOppure ricevi le carte da un altro telefono con il QR.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (favs.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Preferite") }
                items(favs, key = { it.id }) { c -> Tile(c, onOpen) { menuFor = c } }
                if (others.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Tutte le carte") }
            }
            items(others, key = { it.id }) { c -> Tile(c, onOpen) { menuFor = c } }
            if (favs.isEmpty() && others.isEmpty() && query.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Nessuna carta trovata per «$query»", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }

    menuFor?.let { c ->
        CardActionsSheet(
            card = c,
            onDismiss = { menuFor = null },
            onEdit = { menuFor = null; onEdit(c.id) },
            onFavorite = { scope.launch { dao.setFavorite(c.id, !c.favorite) }; menuFor = null },
            onDelete = { scope.launch { deleteCard(context, c) }; menuFor = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(card: Card, onOpen: (Long) -> Unit, onLong: () -> Unit) {
    Column {
        CardTile(card, Modifier.combinedClickable(onClick = { onOpen(card.id) }, onLongClick = onLong))
    }
}
