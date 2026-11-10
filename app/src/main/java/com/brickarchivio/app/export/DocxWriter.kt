package com.brickarchivio.app.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Scrive un file Word (.docx) senza librerie esterne: un .docx è uno zip di file XML.
 * Impaginazione come il modello dell'utente: A4 verticale, margini 2 cm (sopra 2,5 cm),
 * testo 12 pt, tabella a griglia con intestazione grigia in grassetto, contenuto a elenco
 * puntato, colonne Andata/Ritorno centrate. L'intestazione della tabella si ripete se la
 * tabella prosegue sulla pagina successiva.
 *
 * Codice Kotlin puro (nessuna dipendenza Android) così è verificabile con un test JVM.
 */
object DocxWriter {

    fun write(reports: List<InventoryReport>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml", CONTENT_TYPES)
            zip.entry("_rels/.rels", ROOT_RELS)
            zip.entry("word/_rels/document.xml.rels", DOC_RELS)
            zip.entry("word/styles.xml", STYLES)
            zip.entry("word/numbering.xml", NUMBERING)
            zip.entry("word/document.xml", document(reports))
        }
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    internal fun document(reports: List<InventoryReport>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="$W"><w:body>""")
        reports.forEachIndexed { i, r ->
            // ogni inventario su una pagina nuova
            if (i > 0) append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            append(paragraph(r.title))
            append(table(r))
        }
        append(emptyParagraph())
        // A4 verticale: 11906 x 16838 twip; margini come il modello (2 cm lati, 2,5 cm sopra/sotto)
        append(
            """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>""" +
                """<w:pgMar w:top="1417" w:right="1134" w:bottom="1417" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/>""" +
                """</w:sectPr>"""
        )
        append("</w:body></w:document>")
    }

    private fun table(r: InventoryReport): String = buildString {
        val widths = ReportLayout.columnTwips
        append("<w:tbl><w:tblPr>")
        append("""<w:tblStyle w:val="TableGrid"/><w:tblW w:w="0" w:type="auto"/>""")
        append("""<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="1" w:lastColumn="0" w:noHBand="0" w:noVBand="1"/>""")
        append("</w:tblPr><w:tblGrid>")
        widths.forEach { append("""<w:gridCol w:w="$it"/>""") }
        append("</w:tblGrid>")

        // intestazione (ripetuta sulle pagine successive)
        append("<w:tr><w:trPr><w:tblHeader/></w:trPr>")
        ReportLayout.headers.forEachIndexed { c, h ->
            append(cell(widths[c], shaded = true) { paragraph(h, bold = true, center = c >= 2) })
        }
        append("</w:tr>")

        for (box in r.boxes) {
            append("<w:tr>")
            append(cell(widths[0]) { paragraph(box.name) })
            append(cell(widths[1]) {
                if (box.lines.isEmpty()) paragraph("") else box.lines.joinToString("") { bullet(it) }
            })
            append(cell(widths[2]) { paragraph(box.outbound, center = true) })
            append(cell(widths[3]) { paragraph(box.returned, center = true) })
            append("</w:tr>")
        }
        append("</w:tbl>")
    }

    private fun cell(width: Int, shaded: Boolean = false, content: () -> String) = buildString {
        append("""<w:tc><w:tcPr><w:tcW w:w="$width" w:type="dxa"/>""")
        if (shaded) append("""<w:shd w:val="clear" w:color="auto" w:fill="${ReportLayout.HEADER_FILL}"/>""")
        append("</w:tcPr>")
        append(content())
        append("</w:tc>")
    }

    private fun paragraph(text: String, bold: Boolean = false, center: Boolean = false): String = buildString {
        append("<w:p>")
        if (center) append("""<w:pPr><w:jc w:val="center"/></w:pPr>""")
        if (text.isNotEmpty()) append(run(text, bold))
        append("</w:p>")
    }

    private fun bullet(text: String) =
        """<w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>${run(text, false)}</w:p>"""

    private fun emptyParagraph() = "<w:p/>"

    /** Testo con eventuali a capo trasformati in <w:br/>. */
    private fun run(text: String, bold: Boolean): String = buildString {
        append("<w:r>")
        if (bold) append("<w:rPr><w:b/><w:bCs/></w:rPr>")
        text.split('\n').forEachIndexed { i, part ->
            if (i > 0) append("<w:br/>")
            append("""<w:t xml:space="preserve">${escape(part)}</w:t>""")
        }
        append("</w:r>")
    }

    private fun escape(s: String) = buildString {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> if (ch.code >= 0x20 || ch == '\t') append(ch) // caratteri di controllo non ammessi in XML
            }
        }
    }

    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>
</Relationships>"""

    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="$W">
<w:docDefaults>
<w:rPrDefault><w:rPr><w:rFonts w:ascii="Aptos" w:hAnsi="Aptos" w:eastAsia="Aptos" w:cs="Aptos"/><w:sz w:val="24"/><w:szCs w:val="24"/><w:lang w:val="it-IT"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="278" w:lineRule="auto"/></w:pPr></w:pPrDefault>
</w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:ind w:left="720"/><w:contextualSpacing/></w:pPr></w:style>
<w:style w:type="table" w:default="1" w:styleId="TableNormal"><w:name w:val="Normal Table"/><w:tblPr><w:tblInd w:w="0" w:type="dxa"/><w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="108" w:type="dxa"/><w:bottom w:w="0" w:type="dxa"/><w:right w:w="108" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>
<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/><w:basedOn w:val="TableNormal"/><w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:left w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:right w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="auto"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="auto"/></w:tblBorders></w:tblPr></w:style>
</w:styles>"""

    private val NUMBERING = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="$W">
<w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="hybridMultilevel"/>
<w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr><w:rPr><w:rFonts w:ascii="Aptos" w:hAnsi="Aptos"/></w:rPr></w:lvl>
</w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
</w:numbering>"""
}
