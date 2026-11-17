package com.cartefedelta.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cartefedelta.app.barcode.BarcodeEncoder
import com.cartefedelta.app.barcode.CodeFormat
import com.cartefedelta.app.data.AppDatabase
import com.cartefedelta.app.data.Card
import com.cartefedelta.app.share.ShareCodec
import kotlinx.coroutines.delay

/** Mostra il QR (o la sequenza di QR) da far inquadrare all'altro telefono. cardId < 0 = tutte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(cardId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val cards by produceState<List<Card>?>(null, cardId) {
        val dao = AppDatabase.get(context).cards()
        value = if (cardId < 0) dao.all().sortedBy { it.name.lowercase() } else listOfNotNull(dao.get(cardId))
    }
    val qrs = remember(cards) { cards?.takeIf { it.isNotEmpty() }?.let { ShareCodec.encode(it.map(ShareCodec::fromCard)) } }
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }

    MaxBrightness()

    LaunchedEffect(qrs, playing) {
        if (qrs == null || qrs.size < 2) return@LaunchedEffect
        while (playing) {
            delay(900)
            index = (index + 1) % qrs.size
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (cardId < 0) "Condividi tutte le carte" else "Condividi carta") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val list = cards
            when {
                list == null -> Text("Preparazione…")
                list.isEmpty() -> Text("Non ci sono carte da condividere.")
                qrs != null -> {
                    Text(
                        if (list.size == 1) list[0].name else "${list.size} carte",
                        style = MaterialTheme.typography.titleLarge
                    )
                    val i = index.coerceIn(0, qrs.size - 1)
                    val enc = remember(qrs, i) { BarcodeEncoder.encode(qrs[i], CodeFormat.QR_CODE) }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (enc != null) BarcodeCanvas(enc)
                    }
                    if (qrs.size > 1) {
                        Text("Codice ${i + 1} di ${qrs.size}", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = { playing = false; index = (i - 1 + qrs.size) % qrs.size }) { Text("‹ Indietro") }
                            FilledTonalButton(onClick = { playing = !playing }) { Text(if (playing) "Pausa" else "Scorri") }
                            FilledTonalButton(onClick = { playing = false; index = (i + 1) % qrs.size }) { Text("Avanti ›") }
                        }
                    }
                    Text(
                        "Sull'altro telefono apri FreeCard (app o webapp) → menu ⋮ → «Ricevi carte» e inquadra " +
                            (if (qrs.size > 1) "questi codici finché non li ha letti tutti (scorrono da soli)." else "questo codice.") +
                            "\nLe foto delle tessere non vengono inviate: per quelle usa il backup.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
