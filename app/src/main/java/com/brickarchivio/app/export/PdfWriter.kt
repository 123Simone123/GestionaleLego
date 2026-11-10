package com.brickarchivio.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/**
 * Scrive l'inventario in PDF con l'impaginazione del modello Word:
 * A4, margini 2 cm (2,5 cm sopra e sotto), testo 12 pt, tabella a griglia con
 * intestazione grigia ripetuta a ogni pagina, contenuto a elenco puntato.
 * Una riga troppo lunga per la pagina continua nella pagina successiva.
 */
object PdfWriter {

    // misure in punti tipografici (1 pt = 1/72 di pollice); A4 = 595 x 842
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN_X = 56.7f      // 2 cm
    private const val MARGIN_Y = 70.9f      // 2,5 cm
    private const val CELL_PAD_X = 5.4f     // come Word (0,19 cm)
    private const val CELL_PAD_Y = 2f
    private const val FONT_SIZE = 12f
    private const val BULLET_INDENT = 18f
    private const val TITLE_GAP = 8f

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = FONT_SIZE
        color = Color.BLACK
        typeface = Typeface.SANS_SERIF
    }
    private val boldPaint = TextPaint(textPaint).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
    private val linePaint = Paint().apply { color = Color.BLACK; strokeWidth = 0.5f; style = Paint.Style.STROKE }
    private val headerFill = Paint().apply { color = Color.rgb(0xD1, 0xD1, 0xD1); style = Paint.Style.FILL }

    private val colWidths: FloatArray by lazy {
        val total = ReportLayout.columnTwips.sum().toFloat()
        val usable = PAGE_W - 2 * MARGIN_X
        FloatArray(4) { ReportLayout.columnTwips[it] / total * usable }
    }

    fun write(reports: List<InventoryReport>, out: OutputStream) {
        val doc = PdfDocument()
        val w = PageWriter(doc)
        reports.forEachIndexed { i, r ->
            if (i > 0) { w.endTable(); w.newPage() }
            w.title(r.title)
            w.tableHeader()
            r.boxes.forEach { w.row(it) }
        }
        w.finish()
        doc.writeTo(out)
        doc.close()
    }

    private fun layout(text: String, width: Float, paint: TextPaint, center: Boolean = false): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .build()

    /** Gestisce pagine, posizione verticale e intestazione ripetuta. */
    private class PageWriter(private val doc: PdfDocument) {
        private var page: PdfDocument.Page? = null
        private var pageNo = 0
        private var y = 0f
        private var headerOnPage = false
        private var inTable = false
        private val bottom = PAGE_H - MARGIN_Y

        private val canvas: Canvas
            get() {
                if (page == null) newPage()
                return page!!.canvas
            }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            y = MARGIN_Y
            headerOnPage = false
            if (inTable) tableHeader()
        }

        fun endTable() { inTable = false }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
        }

        fun title(text: String) {
            inTable = false
            if (page == null) newPage()
            val l = layout(text, PAGE_W - 2 * MARGIN_X, textPaint)
            draw(l, MARGIN_X, y)
            y += l.height + TITLE_GAP
        }

        fun tableHeader() {
            if (page == null) newPage()
            val cells = ReportLayout.headers.mapIndexed { c, h ->
                layout(h, colWidths[c] - 2 * CELL_PAD_X, boldPaint, center = c >= 2)
            }
            val h = cells.maxOf { it.height } + 2 * CELL_PAD_Y
            var x = MARGIN_X
            for (c in 0 until 4) {
                canvas.drawRect(x, y, x + colWidths[c], y + h, headerFill)
                draw(cells[c], x + CELL_PAD_X, y + CELL_PAD_Y)
                canvas.drawRect(x, y, x + colWidths[c], y + h, linePaint)
                x += colWidths[c]
            }
            y += h
            headerOnPage = true
            inTable = true
        }

        fun row(box: ReportBox) {
            val name = layout(box.name, colWidths[0] - 2 * CELL_PAD_X, textPaint)
            val outL = layout(box.outbound, colWidths[2] - 2 * CELL_PAD_X, textPaint, center = true)
            val retL = layout(box.returned, colWidths[3] - 2 * CELL_PAD_X, textPaint, center = true)
            val bulletWidth = colWidths[1] - 2 * CELL_PAD_X - BULLET_INDENT
            val bullets = box.lines.map { layout(it, bulletWidth, textPaint) }

            var remaining = bullets
            var first = true
            while (true) {
                val available = bottom - y - 2 * CELL_PAD_Y
                // quante righe dell'elenco stanno nello spazio rimasto
                var used = 0f
                var count = 0
                for (b in remaining) {
                    if (used + b.height > available) break
                    used += b.height
                    count++
                }
                val sideHeight = if (first) maxOf(name.height, outL.height, retL.height).toFloat() else name.height.toFloat()
                val fitsAll = count == remaining.size && sideHeight <= available
                if (!fitsAll && count == 0 && headerOnPage && y > MARGIN_Y + 40f) {
                    // non c'è posto neanche per una riga: nuova pagina
                    newPage()
                    continue
                }
                if (!fitsAll && count < remaining.size && first && y > MARGIN_Y + 40f && remaining.sumOf { it.height } < bottom - MARGIN_Y - 60f) {
                    // la riga intera sta in una pagina nuova: meglio non spezzarla
                    newPage()
                    continue
                }
                val chunk = remaining.take(maxOf(count, 1))
                val contentH = chunk.sumOf { it.height }.toFloat()
                val h = maxOf(contentH, sideHeight) + 2 * CELL_PAD_Y
                drawRow(name, chunk, if (first) outL else null, if (first) retL else null, h)
                y += h
                remaining = remaining.drop(chunk.size)
                first = false
                if (remaining.isEmpty()) break
                newPage()
            }
        }

        private fun drawRow(name: StaticLayout, bullets: List<StaticLayout>, outL: StaticLayout?, retL: StaticLayout?, h: Float) {
            var x = MARGIN_X
            draw(name, x + CELL_PAD_X, y + CELL_PAD_Y)
            canvas.drawRect(x, y, x + colWidths[0], y + h, linePaint)
            x += colWidths[0]

            var by = y + CELL_PAD_Y
            for (b in bullets) {
                val baseline = by + b.getLineBaseline(0)
                canvas.drawText("•", x + CELL_PAD_X + 4f, baseline, textPaint)
                draw(b, x + CELL_PAD_X + BULLET_INDENT, by)
                by += b.height
            }
            canvas.drawRect(x, y, x + colWidths[1], y + h, linePaint)
            x += colWidths[1]

            outL?.let { draw(it, x + CELL_PAD_X, y + CELL_PAD_Y) }
            canvas.drawRect(x, y, x + colWidths[2], y + h, linePaint)
            x += colWidths[2]

            retL?.let { draw(it, x + CELL_PAD_X, y + CELL_PAD_Y) }
            canvas.drawRect(x, y, x + colWidths[3], y + h, linePaint)
        }

        private fun draw(l: StaticLayout, x: Float, top: Float) {
            canvas.save()
            canvas.translate(x, top)
            l.draw(canvas)
            canvas.restore()
        }
    }
}
