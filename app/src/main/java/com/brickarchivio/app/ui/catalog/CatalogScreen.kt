package com.brickarchivio.app.ui.catalog

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brickarchivio.app.BrickApp
import com.brickarchivio.app.R
import com.brickarchivio.app.domain.ItemType
import com.brickarchivio.app.domain.LegoItem
import com.brickarchivio.app.domain.subtitle
import com.brickarchivio.app.ui.AppIcon
import com.brickarchivio.app.ui.EmptyState
import com.brickarchivio.app.ui.Pill
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
    val totalCopies: Int = 0,
    val totalSpent: Double = 0.0,
    val grid: Boolean = true,
    val loaded: Boolean = false
)

class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BrickApp
    private val repo = app.repository
    private val filter = MutableStateFlow(CatalogFilter())
    private val grid = MutableStateFlow(app.settings.catalogGrid)

    val state: StateFlow<CatalogUiState> = combine(
        repo.observeItems(), repo.observeTagNames(), filter, grid
    ) { items, tags, f, g ->
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
            totalCopies = filtered.sumOf { it.personal.quantity },
            totalSpent = filtered.sumOf { it.personal.pricePaid ?: 0.0 },
            grid = g,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState(grid = grid.value))

    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setType(t: ItemType?) { filter.value = filter.value.copy(type = t) }
    fun setTag(t: String?) { filter.value = filter.value.copy(tag = t) }
    fun toggleGrid() {
        grid.value = !grid.value
        app.settings.catalogGrid = grid.value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onOpenItem: (Long) -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    vm: CatalogViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Brick Archivio") },
                actions = {
                    IconButton(onClick = vm::toggleGrid) {
                        AppIcon(if (state.grid) R.drawable.ic_list else R.drawable.ic_grid, if (state.grid) "Vista lista" else "Vista griglia")
                    }
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Add, "Aggiungi cercando") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { AppIcon(R.drawable.ic_camera, null) },
                text = { Text("Scansiona") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Ricerca
            TextField(
                value = state.filter.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Cerca per nome, numero, tema, tag…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.filter.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, "Cancella") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            // Filtri
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
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
            // Riepilogo
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Stat("Voci", state.items.size.toString(), Modifier.weight(1f))
                Stat("Esemplari", state.totalCopies.toString(), Modifier.weight(1f))
                Stat("Speso", formatPrice(state.totalSpent), Modifier.weight(1.3f))
            }

            when {
                !state.loaded -> Unit
                state.items.isEmpty() && state.filter == CatalogFilter() -> EmptyState(
                    R.drawable.ic_brick, "Catalogo vuoto",
                    "Tocca «Scansiona» per fotografare i tuoi set, oppure + per cercarli per numero o nome."
                )
                state.items.isEmpty() -> EmptyState(R.drawable.ic_brick, "Nessun risultato", "Prova a cambiare ricerca o filtri.")
                state.grid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(156.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.id }) { item -> GridCard(item) { onOpenItem(item.id) } }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.id }) { item -> ListCard(item) { onOpenItem(item.id) } }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TypePill(item: LegoItem) {
    val (bg, fg) = when (item.type) {
        ItemType.SET -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ItemType.MINIFIG -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ItemType.PART -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Pill(item.type.label, bg, fg)
}

@Composable
private fun GridCard(item: LegoItem, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box {
            Thumb(item.mainImage(), 156.dp, Modifier.fillMaxWidth().aspectRatio(1f))
            if (item.personal.quantity > 1) {
                Pill(
                    "×${item.personal.quantity}",
                    MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary,
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(item.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            TypePill(item)
        }
    }
}

@Composable
private fun ListCard(item: LegoItem, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(item.mainImage(), 68.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypePill(item)
                    item.personal.tags.take(2).forEach {
                        Pill("#$it", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (item.personal.quantity > 1) {
                Text("×${item.personal.quantity}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
