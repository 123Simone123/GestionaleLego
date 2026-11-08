package com.brickarchivio.app.recognition

import android.graphics.Bitmap
import com.brickarchivio.app.data.NormRect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Rilevamento degli oggetti per differenza dallo sfondo (offline, nessun limite al
 * numero di oggetti). Pensato per minifigure e pezzi appoggiati su una superficie
 * uniforme (foglio, tavolo, tovaglia).
 *
 * Algoritmo:
 *  1. riduce la foto a ~320 px;
 *  2. stima il colore dello sfondo dalla cornice esterna (mediana);
 *  3. se la cornice non è uniforme rinuncia (restituisce null → si usa ML Kit);
 *  4. marca come "oggetto" i pixel lontani dal colore di sfondo (la luminosità pesa
 *     meno della tinta, per ignorare in parte le ombre);
 *  5. dilata la maschera per unire le parti di uno stesso oggetto (gambe/busto/testa);
 *  6. trova le componenti connesse e ne prende i rettangoli.
 */
object BackgroundSegmenter {

    private const val WORK_SIZE = 320
    private const val BORDER = 4

    /** null = sfondo non uniforme, metodo non applicabile. */
    fun detect(bitmap: Bitmap): List<NormRect>? {
        val scale = WORK_SIZE.toFloat() / max(bitmap.width, bitmap.height)
        val w = max(1, (bitmap.width * min(1f, scale)).roundToInt())
        val h = max(1, (bitmap.height * min(1f, scale)).roundToInt())
        val small = if (w == bitmap.width && h == bitmap.height) bitmap
        else Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()

        // luma + crominanza per ogni pixel
        val y = FloatArray(px.size)
        val cb = FloatArray(px.size)
        val cr = FloatArray(px.size)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16 and 0xFF).toFloat()
            val g = (c shr 8 and 0xFF).toFloat()
            val b = (c and 0xFF).toFloat()
            y[i] = 0.299f * r + 0.587f * g + 0.114f * b
            cb[i] = -0.1687f * r - 0.3313f * g + 0.5f * b
            cr[i] = 0.5f * r - 0.4187f * g - 0.0813f * b
        }

        // 2. colore di sfondo = mediana della cornice
        val border = ArrayList<Int>()
        for (yy in 0 until h) for (xx in 0 until w) {
            if (xx < BORDER || yy < BORDER || xx >= w - BORDER || yy >= h - BORDER) border += yy * w + xx
        }
        if (border.isEmpty()) return null
        val bgY = median(border.map { y[it] })
        val bgCb = median(border.map { cb[it] })
        val bgCr = median(border.map { cr[it] })

        fun dist(i: Int): Float {
            val dy = (y[i] - bgY) * 0.45f
            val dcb = cb[i] - bgCb
            val dcr = cr[i] - bgCr
            return sqrt(dy * dy + dcb * dcb + dcr * dcr)
        }

        // 3. uniformità della cornice e soglia adattiva
        val borderDist = border.map { dist(it) }.sorted()
        val spread = borderDist[(borderDist.size * 0.8f).toInt().coerceAtMost(borderDist.size - 1)]
        if (spread > 28f) return null // cornice troppo variegata: sfondo non uniforme
        val threshold = max(22f, spread * 2.2f + 10f)

        // 4. maschera
        val mask = BooleanArray(px.size) { dist(it) > threshold }

        // 5. dilatazione (raggio proporzionale alla dimensione)
        val radius = max(2, (max(w, h) * 0.012f).roundToInt())
        val dilated = dilate(mask, w, h, radius)

        // 6. componenti connesse
        val minArea = w * h * 0.002f
        val labels = IntArray(px.size)
        val boxes = mutableListOf<IntArray>() // minX, minY, maxX, maxY, area
        val stack = IntArray(px.size)
        var label = 0
        for (start in px.indices) {
            if (!dilated[start] || labels[start] != 0) continue
            label++
            var sp = 0
            stack[sp++] = start
            labels[start] = label
            var minX = w; var minY = h; var maxX = 0; var maxY = 0; var area = 0
            while (sp > 0) {
                val p = stack[--sp]
                val px0 = p % w
                val py0 = p / w
                if (mask[p]) area++
                if (px0 < minX) minX = px0
                if (px0 > maxX) maxX = px0
                if (py0 < minY) minY = py0
                if (py0 > maxY) maxY = py0
                if (px0 > 0) push(p - 1, dilated, labels, label, stack, sp)?.let { sp = it }
                if (px0 < w - 1) push(p + 1, dilated, labels, label, stack, sp)?.let { sp = it }
                if (py0 > 0) push(p - w, dilated, labels, label, stack, sp)?.let { sp = it }
                if (py0 < h - 1) push(p + w, dilated, labels, label, stack, sp)?.let { sp = it }
            }
            if (area >= minArea) boxes += intArrayOf(minX, minY, maxX, maxY, area)
        }

        val rects = boxes
            .sortedByDescending { it[4] }
            .take(30)
            .map { b ->
                val padX = (b[2] - b[0]) * 0.08f
                val padY = (b[3] - b[1]) * 0.08f
                NormRect(
                    (b[0] - padX) / w, (b[1] - padY) / h,
                    (b[2] + 1 + padX) / w, (b[3] + 1 + padY) / h
                ).normalized()
            }
        return mergeContained(rects)
    }

    private fun push(p: Int, dilated: BooleanArray, labels: IntArray, label: Int, stack: IntArray, sp: Int): Int? {
        if (!dilated[p] || labels[p] != 0) return null
        labels[p] = label
        stack[sp] = p
        return sp + 1
    }

    private fun dilate(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        // separabile: prima orizzontale poi verticale (quadrato di lato 2r+1)
        val tmp = BooleanArray(mask.size)
        for (yy in 0 until h) {
            var last = -10_000
            for (xx in 0 until w) { if (mask[yy * w + xx]) last = xx; if (xx - last <= r) tmp[yy * w + xx] = true }
            last = 10_000
            for (xx in w - 1 downTo 0) { if (mask[yy * w + xx]) last = xx; if (last - xx <= r) tmp[yy * w + xx] = true }
        }
        val out = BooleanArray(mask.size)
        for (xx in 0 until w) {
            var last = -10_000
            for (yy in 0 until h) { if (tmp[yy * w + xx]) last = yy; if (yy - last <= r) out[yy * w + xx] = true }
            last = 10_000
            for (yy in h - 1 downTo 0) { if (tmp[yy * w + xx]) last = yy; if (last - yy <= r) out[yy * w + xx] = true }
        }
        return out
    }

    /** Elimina i rettangoli contenuti quasi interamente in uno più grande. */
    private fun mergeContained(rects: List<NormRect>): List<NormRect> {
        val sorted = rects.sortedByDescending { it.width * it.height }
        val kept = mutableListOf<NormRect>()
        for (r in sorted) {
            val inside = kept.any { k ->
                val ix = max(0f, min(k.right, r.right) - max(k.left, r.left))
                val iy = max(0f, min(k.bottom, r.bottom) - max(k.top, r.top))
                ix * iy > 0.85f * r.width * r.height
            }
            if (!inside) kept += r
        }
        return kept
    }

    private fun median(values: List<Float>): Float {
        val s = values.sorted()
        return s[s.size / 2]
    }
}
