package com.brickarchivio.app.ui.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.brickarchivio.app.domain.Exhibition
import com.brickarchivio.app.export.CheckMode
import com.brickarchivio.app.export.ExportFormat
import com.brickarchivio.app.export.ReportBuilder
import com.brickarchivio.app.export.ReportExporter
import com.brickarchivio.app.ui.SectionTitle
import kotlinx.coroutines.launch

/**
 * Esportazione dell'inventario di un'esposizione in Word o PDF (layout del modello
 * INVENTARIO.docx). "Condividi" apre il menu di Android (WhatsApp, email, Drive, stampa…);
 * "Salva" chiede dove salvare il file sul telefono.
 */
@Composable
fun ExportDialog(ex: Exhibition, onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var mode by remember { mutableStateOf(CheckMode.BLANK) }
    var busy by remember { mutableStateOf(false) }

    fun reports() = listOf(ReportBuilder.fromExhibition(ex, mode))

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(format.mimeType)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val reports = reports()
        val fmt = format
        scope.launch {
            val msg = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { ReportExporter.write(reports, fmt, it) }
                    ?: error("impossibile scrivere il file")
            }.fold({ "Inventario salvato" }, { "Errore: ${it.message}" })
            onResult(msg)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Esporta inventario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    ReportBuilder.title(ex),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SectionTitle("Formato")
                ExportFormat.entries.forEach { f ->
                    Choice(f.label + if (f == ExportFormat.DOCX) " (.docx, modificabile)" else " (pronto da stampare)", format == f) { format = f }
                }
                SectionTitle("Colonne Andata e Ritorno")
                CheckMode.entries.forEach { m -> Choice(m.label, mode == m) { mode = m } }
                if (ex.sections.isEmpty()) {
                    Text(
                        "L'esposizione non ha contenitori: il documento avrà solo l'intestazione.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                val reports = reports()
                val fmt = format
                scope.launch {
                    runCatching {
                        val intent = ReportExporter.shareIntent(context, reports, fmt, ReportExporter.fileName(ex, fmt))
                        context.startActivity(intent)
                    }.onFailure { onResult("Errore: ${it.message}") }
                    busy = false
                    onDismiss()
                }
            }) { Text("Condividi") }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = { saveLauncher.launch(ReportExporter.fileName(ex, format)) }) { Text("Salva") }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        }
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
