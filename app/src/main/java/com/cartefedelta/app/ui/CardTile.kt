package com.cartefedelta.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cartefedelta.app.data.Card
import com.cartefedelta.app.data.Photos

/** Aspetto di una tessera: foto del fronte se c'è, altrimenti colore + nome. */
@Composable
fun CardFace(
    name: String,
    color: Int,
    frontPhoto: String?,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    big: Boolean = false
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(if (big) 18.dp else 12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.586f) // proporzioni di una carta di credito
            .shadow(if (big) 6.dp else 3.dp, shape)
            .clip(shape)
            .background(Color(color))
    ) {
        if (frontPhoto != null) {
            AsyncImage(
                model = Photos.file(context, frontPhoto),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0x99000000))
                )
            )
            Text(
                name,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = if (big) 20.sp else 13.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(if (big) 16.dp else 8.dp)
            )
        } else {
            Text(
                name,
                color = contentColorFor(color),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = if (big) 30.sp else if (name.length > 12) 15.sp else 19.sp,
                lineHeight = if (big) 34.sp else 20.sp,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 10.dp)
            )
        }
        if (favorite) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Preferita",
                tint = Color(0xFFFFD54F),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(if (big) 26.dp else 18.dp)
            )
        }
    }
}

@Composable
fun CardTile(card: Card, modifier: Modifier = Modifier) =
    CardFace(card.name, card.color, card.frontPhoto, card.favorite, modifier)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp)
    )
}
