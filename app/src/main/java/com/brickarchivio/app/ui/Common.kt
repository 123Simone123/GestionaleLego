package com.brickarchivio.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.brickarchivio.app.BrickApp
import com.brickarchivio.app.domain.LegoItem
import java.io.File
import java.text.NumberFormat
import java.util.Locale

private val Red = Color(0xFFC62828)
private val Yellow = Color(0xFFFFD600)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFFEF9A9A), secondary = Yellow, tertiary = Color(0xFF90CAF9))
    } else {
        lightColorScheme(primary = Red, secondary = Color(0xFFF9A825), tertiary = Color(0xFF1565C0))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

val Context.app: BrickApp get() = applicationContext as BrickApp

fun formatPrice(value: Double?): String =
    value?.let { NumberFormat.getCurrencyInstance(Locale.ITALY).format(it) } ?: "—"

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** Modello da passare a Coil: file locale se c'è, altrimenti URL. */
fun imageModel(localPath: String?, url: String?): Any? =
    localPath?.let { File(it) }?.takeIf { it.exists() } ?: url

/** Immagine principale di un oggetto: ufficiale (locale o online), altrimenti la prima foto. */
fun LegoItem.mainImage(): Any? =
    imageModel(officialImage.localPath, officialImage.url) ?: photos.firstOrNull()?.let { File(it.path) }

@Composable
fun Thumb(model: Any?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size)
            )
        } else {
            Text("?", style = MaterialTheme.typography.titleLarge)
        }
    }
}
