package com.cartefedelta.app.ui

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cartefedelta.app.barcode.CodeFormat
import com.cartefedelta.app.data.AppDatabase
import com.cartefedelta.app.data.Photos
import kotlinx.coroutines.launch

/** Carta aperta alla cassa: codice grande, luminosità al massimo, schermo sempre acceso. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(cardId: Long, onEdit: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).cards() }
    val card by dao.observe(cardId).collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var zoomPhoto by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cardId) { dao.markUsed(cardId) }

    // Luminosità massima finché la carta è aperta
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val previous = window?.attributes?.screenBrightness
        window?.let {
            it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val c = card
    val barColor = c?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.surface
    val onBar = c?.color?.let { contentColorFor(it) } ?: MaterialTheme.colorScheme.onSurface

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c?.name ?: "") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barColor, titleContentColor = onBar,
                    navigationIconContentColor = onBar, actionIconContentColor = onBar
                ),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    if (c != null) {
                        IconButton(onClick = { scope.launch { dao.setFavorite(c.id, !c.favorite) } }) {
                            Icon(Icons.Filled.Star, "Preferita", tint = if (c.favorite) Color(0xFFFFD54F) else onBar.copy(alpha = 0.5f))
                        }
                        IconButton(onClick = {
                            val text = "${c.name}: ${c.displayNumber.ifBlank { c.number }}"
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Condividi numero"
                            ))
                        }) { Icon(Icons.Filled.Share, "Condividi") }
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Modifica") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Elimina") }
                    }
                }
            )
        }
    ) { padding ->
        if (c == null) return@Scaffold
        val format = CodeFormat.of(c.format)
        val enc = rememberEncoded(c.number, format)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Il codice da mostrare alla cassa
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (enc != null) BarcodeCanvas(enc) else Text("Impossibile disegnare il codice", color = Color.Red)
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Text(
                        c.displayNumber.ifBlank { c.number },
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    (enc?.format ?: format).label,
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            CardFace(c.name, c.color, c.frontPhoto, c.favorite, big = true)

            if (c.frontPhoto != null || c.backPhoto != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOfNotNull(c.frontPhoto, c.backPhoto).forEach { p ->
                        AsyncImage(
                            model = Photos.file(context, p),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.586f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { zoomPhoto = p }
                        )
                    }
                }
            }

            if (c.note.isNotBlank()) {
                Column(Modifier.fillMaxWidth()) {
                    SectionTitle("Note")
                    Text(c.note)
                }
            }
            Text(
                "Usata ${c.useCount} ${if (c.useCount == 1) "volta" else "volte"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmDelete && c != null) {
        ConfirmDelete(c.name, onConfirm = {
            confirmDelete = false
            scope.launch {
                deleteCard(context, c)
                onBack()
            }
        }, onDismiss = { confirmDelete = false })
    }

    zoomPhoto?.let { p ->
        Dialog(onDismissRequest = { zoomPhoto = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { zoomPhoto = null }, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = Photos.file(context, p),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
