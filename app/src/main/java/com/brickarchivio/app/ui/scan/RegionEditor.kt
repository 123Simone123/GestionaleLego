package com.brickarchivio.app.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brickarchivio.app.data.NormRect
import kotlin.math.hypot

private const val MIN_SIZE = 0.05f

/**
 * Mostra la foto con i riquadri degli oggetti.
 * - tocco su un riquadro: lo seleziona;
 * - trascinamento dentro un riquadro: lo sposta;
 * - trascinamento di un angolo del riquadro selezionato: lo ridimensiona.
 */
@Composable
fun RegionEditor(
    image: ImageBitmap,
    regions: List<RegionUi>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    onChange: (Int, NormRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRegions by rememberUpdatedState(regions)
    val currentSelected by rememberUpdatedState(selectedId)
    val measurer = rememberTextMeasurer()
    val ratio = image.width.toFloat() / image.height.toFloat()

    Box(modifier.aspectRatio(ratio)) {
        Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val x = pos.x / size.width
                        val y = pos.y / size.height
                        onSelect(currentRegions.lastOrNull { it.rect.contains(x, y) }?.id)
                    }
                }
                .pointerInput(Unit) {
                    var targetId = -1
                    var corner = -1
                    detectDragGestures(
                        onDragStart = { pos ->
                            targetId = -1
                            corner = -1
                            val handle = 32.dp.toPx()
                            val sel = currentRegions.firstOrNull { it.id == currentSelected }
                            if (sel != null) {
                                val r = sel.rect
                                val corners = listOf(
                                    Offset(r.left * size.width, r.top * size.height),
                                    Offset(r.right * size.width, r.top * size.height),
                                    Offset(r.left * size.width, r.bottom * size.height),
                                    Offset(r.right * size.width, r.bottom * size.height)
                                )
                                corner = corners.indexOfFirst { hypot(it.x - pos.x, it.y - pos.y) < handle }
                                if (corner >= 0) targetId = sel.id
                            }
                            if (targetId < 0) {
                                val x = pos.x / size.width
                                val y = pos.y / size.height
                                val hit = currentRegions.lastOrNull { it.rect.contains(x, y) }
                                if (hit != null) {
                                    targetId = hit.id
                                    onSelect(hit.id)
                                }
                            }
                        },
                        onDragEnd = { targetId = -1 },
                        onDragCancel = { targetId = -1 },
                        onDrag = { change, amount ->
                            val r = currentRegions.firstOrNull { it.id == targetId }?.rect
                            if (r != null) {
                                change.consume()
                                val dx = amount.x / size.width
                                val dy = amount.y / size.height
                                val next = when (corner) {
                                    0 -> r.copy(left = r.left + dx, top = r.top + dy)
                                    1 -> r.copy(right = r.right + dx, top = r.top + dy)
                                    2 -> r.copy(left = r.left + dx, bottom = r.bottom + dy)
                                    3 -> r.copy(right = r.right + dx, bottom = r.bottom + dy)
                                    else -> {
                                        val nl = (r.left + dx).coerceIn(0f, 1f - r.width)
                                        val nt = (r.top + dy).coerceIn(0f, 1f - r.height)
                                        NormRect(nl, nt, nl + r.width, nt + r.height)
                                    }
                                }
                                if (next.width >= MIN_SIZE && next.height >= MIN_SIZE) {
                                    onChange(targetId, next.normalized())
                                }
                            }
                        }
                    )
                }
        ) {
            regions.forEachIndexed { i, region ->
                val r = region.rect
                val selected = region.id == selectedId
                val color = if (selected) Color(0xFFFFD600) else Color(0xFF00E5FF)
                val topLeft = Offset(r.left * size.width, r.top * size.height)
                val boxSize = Size(r.width * size.width, r.height * size.height)
                drawRect(Color.Black.copy(alpha = 0.35f), topLeft, boxSize, style = Stroke(width = 7.dp.toPx()))
                drawRect(color, topLeft, boxSize, style = Stroke(width = 3.dp.toPx()))
                drawText(
                    measurer,
                    "${i + 1}",
                    topLeft = topLeft + Offset(6.dp.toPx(), 2.dp.toPx()),
                    style = TextStyle(color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                if (selected) {
                    listOf(
                        topLeft,
                        topLeft + Offset(boxSize.width, 0f),
                        topLeft + Offset(0f, boxSize.height),
                        topLeft + Offset(boxSize.width, boxSize.height)
                    ).forEach { drawCircle(color, radius = 9.dp.toPx(), center = it) }
                }
            }
        }
    }
}
