package com.cartefedelta.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cartefedelta.app.barcode.BarcodeEncoder
import com.cartefedelta.app.barcode.CodeFormat
import com.cartefedelta.app.barcode.Encoded
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Composable
fun rememberEncoded(content: String, format: CodeFormat): Encoded? =
    remember(content, format) { BarcodeEncoder.encode(content, format) }

/** Disegna il codice nero su bianco, con moduli a pixel interi perché i lettori lo leggano bene. */
@Composable
fun BarcodeCanvas(encoded: Encoded, modifier: Modifier = Modifier) {
    val m = encoded.matrix
    val sized = if (encoded.format.twoD) {
        modifier.fillMaxWidth(0.75f).aspectRatio(m.width.toFloat() / m.height.toFloat())
    } else {
        modifier.fillMaxWidth().height(130.dp)
    }
    Canvas(sized) {
        drawRect(Color.White)
        if (encoded.format.twoD) {
            val cell = max(1f, floor(min(size.width / m.width, size.height / m.height)))
            val ox = floor((size.width - cell * m.width) / 2)
            val oy = floor((size.height - cell * m.height) / 2)
            for (y in 0 until m.height) for (x in 0 until m.width) {
                if (m.get(x, y)) drawRect(Color.Black, Offset(ox + x * cell, oy + y * cell), Size(cell, cell))
            }
        } else {
            val bar = max(1f, floor(size.width / m.width))
            val ox = floor((size.width - bar * m.width) / 2)
            var x = 0
            while (x < m.width) {
                if (m.get(x, 0)) {
                    var end = x
                    while (end < m.width && m.get(end, 0)) end++
                    drawRect(Color.Black, Offset(ox + x * bar, 0f), Size(bar * (end - x), size.height))
                    x = end
                } else x++
            }
        }
    }
}
