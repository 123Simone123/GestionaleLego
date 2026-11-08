package com.brickarchivio.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.brickarchivio.app.BrickApp
import com.brickarchivio.app.R
import com.brickarchivio.app.domain.LegoItem
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Tema ---------------------------------------------------------------------------

object BrickColors {
    val Red = Color(0xFFC91A1F)
    val RedDark = Color(0xFF8E0F13)
    val Yellow = Color(0xFFF5C518)
    val Blue = Color(0xFF1E5AA8)
    val Green = Color(0xFF2E7D32)
    val Warm = Color(0xFFFBF8F3)
}

private val LightColors = lightColorScheme(
    primary = BrickColors.Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF7A5D00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = BrickColors.Blue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E3FF),
    onTertiaryContainer = Color(0xFF001B3E),
    background = BrickColors.Warm,
    surface = BrickColors.Warm,
    surfaceVariant = Color(0xFFF1E9E1),
    surfaceContainer = Color(0xFFF6F0E9),
    surfaceContainerLow = Color(0xFFFFFBF7),
    surfaceContainerHigh = Color(0xFFEFE8E0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = BrickColors.RedDark,
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = BrickColors.Yellow,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5C4400),
    onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = Color(0xFFAAC7FF),
    tertiaryContainer = Color(0xFF00468C),
    onTertiaryContainer = Color(0xFFD6E3FF)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val typography = base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    )
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = typography,
        shapes = AppShapes,
        content = content
    )
}

// --- Utilità -----------------------------------------------------------------------

val Context.app: BrickApp get() = applicationContext as BrickApp

fun formatPrice(value: Double?): String =
    value?.let { NumberFormat.getCurrencyInstance(Locale.ITALY).format(it) } ?: "—"

private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
private val itDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** "2026-11-08" → "08/11/2026" */
fun formatDate(iso: String?): String? =
    iso?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it, isoDate).format(itDate) }.getOrDefault(it) }

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** Modello da passare a Coil: file locale se c'è, altrimenti URL. */
fun imageModel(localPath: String?, url: String?): Any? =
    localPath?.let { File(it) }?.takeIf { it.exists() } ?: url

/** Immagine principale di un oggetto: ufficiale (locale o online), altrimenti la prima foto. */
fun LegoItem.mainImage(): Any? =
    imageModel(officialImage.localPath, officialImage.url) ?: photos.firstOrNull()?.let { File(it.path) }

// --- Componenti --------------------------------------------------------------------

@Composable
fun AppIcon(@DrawableRes id: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color? = null) {
    if (tint != null) Icon(painterResource(id), contentDescription, modifier, tint = tint)
    else Icon(painterResource(id), contentDescription, modifier)
}

/** Miniatura con sfondo chiaro (le immagini ufficiali hanno spesso il fondo bianco). */
@Composable
fun Thumb(model: Any?, size: Dp, modifier: Modifier = Modifier, @DrawableRes placeholder: Int = R.drawable.ic_brick) {
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size * 0.06f)
            )
        } else {
            AppIcon(placeholder, null, Modifier.size(size * 0.45f), tint = Color(0xFFBDB5AC))
        }
    }
}

/** Messaggio centrato per liste vuote. */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            AppIcon(icon, null, Modifier.padding(20.dp).size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp).widthIn(max = 320.dp)
        )
    }
}

/** Piccola etichetta colorata (stato, quantità…). */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

/** Titolo di sezione. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

/** − quantità + */
@Composable
fun QuantityStepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { if (value > 1) onChange(value - 1) }, enabled = value > 1, modifier = Modifier.size(32.dp)) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 10.dp))
        FilledTonalIconButton(onClick = { onChange(value + 1) }, modifier = Modifier.size(32.dp)) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Campo data (formato ISO salvato, mostrato gg/mm/aaaa) con calendario. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, isoValue: String?, onChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = formatDate(isoValue) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val initial = isoValue?.let { v ->
            runCatching { LocalDate.parse(v, isoDate).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(isoDate)
                    })
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { onChange(null); open = false }) { Text("Cancella") } }
        ) { DatePicker(state = state) }
    }
}
