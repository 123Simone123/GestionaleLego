package com.cartefedelta.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cartefedelta.app.data.Store
import com.cartefedelta.app.data.Stores

/** Scelta del negozio: lista preimpostata con ricerca, oppure carta personalizzata. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorePickerScreen(onPick: (Store?, String) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val filtered = Stores.all.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scegli il negozio") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Cerca negozio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(null, q) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp, 28.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        if (q.isEmpty()) "Altra carta (nome a scelta)" else "Crea «$q»",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                HorizontalDivider()
            }
            var lastCat: String? = null
            filtered.forEach { store ->
                if (store.category != lastCat) {
                    val cat = store.category
                    item(key = "cat-$cat") { SectionTitle(cat, Modifier.padding(start = 16.dp, top = 8.dp)) }
                    lastCat = cat
                }
                item(key = store.key) { StoreRow(store) { onPick(store, store.name) } }
            }
        }
    }
}

@Composable
private fun StoreRow(store: Store, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp, 28.dp).clip(RoundedCornerShape(6.dp)).background(Color(store.color)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                store.name.take(1),
                color = contentColorFor(store.color.toInt()),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(store.name, style = MaterialTheme.typography.bodyLarge)
    }
}
