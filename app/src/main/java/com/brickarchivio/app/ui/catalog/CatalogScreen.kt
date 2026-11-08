package com.brickarchivio.app.ui.catalog

import android.app.Application
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brickarchivio.app.BrickApp
import com.brickarchivio.app.domain.ItemType
import com.brickarchivio.app.domain.LegoItem
import com.brickarchivio.app.domain.subtitle
import com.brickarchivio.app.ui.Thumb
import com.brickarchivio.app.ui.formatPrice
import com.brickarchivio.app.ui.mainImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CatalogFilter(
    val query: String = "",
    val type: ItemType? = null,
    val tag: String? = null
)

data class CatalogUiState(
    val items: List<LegoItem> = emptyList(),
    val tags: List<String> = emptyList(),
    val filter: CatalogFilter = CatalogFilter(),
    val totalPieces: Int = 0,
    val totalSpent: Double = 0.0
)

class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as BrickApp).repository
    private val filter = MutableStateFlow(CatalogFilter())

    val state: StateFlow<CatalogUiState> = combine(
        repo.observeItems(), repo.observeTagNames(), filter
    ) { items, tags, f ->
        val q = f.query.trim().lowercase()
        val filtered = items.filter { item ->
            (f.type == null || item.type == f.type) &&
                (f.tag == null || f.tag in item.personal.tags) &&
                (q.isEmpty() || item.name.lowercase().contains(q) || item.code.lowercase().contains(q) ||
                    item.personal.notes.orEmpty().lowercase().contains(q) ||
                    item.personal.tags.any { it.lowercase().contains(q) } ||
                    (item is LegoItem.LegoSet && item.theme.orEmpty().lowercase().contains(q)))
        }
        CatalogUiState(
            items = filtered,
            tags = tags,
            filter = f,
            totalPieces = filtered.sumOf { it.personal.quantity },
            totalSpent = filtered.sumOf { it.personal.pricePaid ?: 0.0 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setType(t: ItemType?) { filter.value = filter.value.copy(type = t) }
    fun setTag(t: String?) { filter.value = filter.value.copy(tag = t) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onOpenItem: (Long) -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    vm: CatalogViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brick Archivio") },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Cerca e aggiungi") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Impostazioni") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Scansiona") }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Filtra per nome, numero, tema, tag…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = state.filter.type == null, onClick = { vm.setType(null) }, label = { Text("Tutti") })
                ItemType.entries.forEach { t ->
                    FilterChip(
                        selected = state.filter.type == t,
                        onClick = { vm.setType(if (state.filter.type == t) null else t) },
                        label = { Text(t.label) }
                    )
                }
                state.tags.forEach { tag ->
                    FilterChip(
                        selected = state.filter.tag == tag,
                        onClick = { vm.setTag(if (state.filter.tag == tag) null else tag) },
                        label = { Text("#$tag") }
                    )
                }
            }
            Text(
                "${state.items.size} voci · ${state.totalPieces} esemplari · speso ${formatPrice(state.totalSpent)}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.filter == CatalogFilter()) "Il catalogo è vuoto.\nTocca «Scansiona» per fotografare i tuoi set."
                        else "Nessun risultato con questi filtri.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(state.items, key = { it.id }) { item ->
                        ItemRow(item, onClick = { onOpenItem(item.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: LegoItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(item.mainImage(), 64.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                item.type.label + if (item.personal.tags.isNotEmpty()) "  ·  " + item.personal.tags.joinToString(" ") { "#$it" } else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.personal.quantity > 1) {
            Text("×${item.personal.quantity}", style = MaterialTheme.typography.titleMedium)
        }
    }
}
