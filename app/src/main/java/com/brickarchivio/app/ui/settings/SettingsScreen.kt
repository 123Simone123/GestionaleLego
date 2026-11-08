package com.brickarchivio.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.brickarchivio.app.BuildConfigInfo
import com.brickarchivio.app.MainActivity
import com.brickarchivio.app.data.Backup
import com.brickarchivio.app.ui.app
import com.brickarchivio.app.ui.openUrl
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var key by remember { mutableStateOf(app.settings.rebrickableKey.orEmpty()) }
    var showKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val msg = runCatching { Backup.export(context, app.database, uri) }
                .fold({ "Backup salvato" }, { "Errore: ${it.message}" })
            busy = false
            snackbar.showSnackbar(msg)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Chiave API Rebrickable", style = MaterialTheme.typography.titleMedium)
            Text(
                "Serve per nome, anno, tema, numero di pezzi e immagine ufficiale dei set. " +
                    "È gratuita: crea un account su rebrickable.com, poi Account → Settings → API.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                singleLine = true,
                label = { Text("Chiave") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    app.settings.rebrickableKey = key
                    scope.launch {
                        busy = true
                        val ok = app.rebrickable.testKey()
                        busy = false
                        snackbar.showSnackbar(if (ok) "Chiave salvata e funzionante" else "Chiave salvata, ma la verifica è fallita")
                    }
                }, enabled = !busy) { Text("Salva e verifica") }
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Nascondi" else "Mostra") }
            }
            TextButton(onClick = { openUrl(context, "https://rebrickable.com/api/") }) { Text("Apri rebrickable.com/api") }

            HorizontalDivider()
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tutti i dati e le foto sono solo su questo telefono. Se disinstalli l'app vengono cancellati: " +
                    "fai ogni tanto un backup (un file .zip) e conservalo, ad esempio su Drive o sul PC.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("brickarchivio-backup-${LocalDate.now()}.zip") }, enabled = !busy) {
                    Text("Esporta backup")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy) {
                    Text("Importa backup")
                }
            }

            HorizontalDivider()
            Text("Informazioni", style = MaterialTheme.typography.titleMedium)
            Text(
                "Versione ${BuildConfigInfo.versionName(context)}\n" +
                    "Riconoscimento: Brickognize (brickognize.com) e ML Kit sul telefono.\n" +
                    "Dati dei set: Rebrickable (rebrickable.com).\n" +
                    "LEGO® è un marchio del Gruppo LEGO, che non sponsorizza né approva questa app.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Importare il backup?") },
            text = { Text("Il catalogo attuale verrà SOSTITUITO da quello del backup. L'app si riavvierà.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    scope.launch {
                        busy = true
                        val result = runCatching { Backup.import(context, app.database, uri) }
                        if (result.isSuccess) {
                            val intent = Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            context.startActivity(intent)
                            exitProcess(0)
                        } else {
                            busy = false
                            snackbar.showSnackbar("Errore: ${result.exceptionOrNull()?.message}")
                        }
                    }
                }) { Text("Importa") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Annulla") } }
        )
    }
}
