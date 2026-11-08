package com.brickarchivio.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brickarchivio.app.data.remote.Candidate
import com.brickarchivio.app.domain.ItemType
import com.brickarchivio.app.ui.Thumb
import com.brickarchivio.app.ui.app
import kotlinx.coroutines.launch

/**
 * Pannello di ricerca manuale su Rebrickable (per numero o nome).
 * Usato sia nella schermata "Cerca e aggiungi" sia per correggere un riconoscimento.
 */
@Composable
fun ManualSearchPanel(
    modifier: Modifier = Modifier,
    initialType: ItemType = ItemType.SET,
    onPick: (Candidate) -> Unit
) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(initialType) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Candidate>>(emptyList()) }

    fun runSearch() {
        if (query.isBlank()) return
        scope.launch {
            loading = true
            error = null
            results = try {
                repo.search(type, query).also { if (it.isEmpty()) error = "Nessun risultato" }
            } catch (e: Exception) {
                error = e.message
                emptyList()
            }
            loading = false
        }
    }

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemType.entries.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t; results = emptyList() }, label = { Text(t.label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = {
                    Text(
                        when (type) {
                            ItemType.SET -> "Numero o nome (es. 75192)"
                            ItemType.MINIFIG -> "Nome o fig-000001"
                            ItemType.PART -> "Numero o nome (es. 3001)"
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { runSearch() }, enabled = !loading) { Text("Cerca") }
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }
        LazyColumn {
            items(results, key = { "${it.type}|${it.code}" }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Thumb(c.imageUrl, 56.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(c.code, c.year?.toString(), c.numParts?.let { "$it pezzi" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
