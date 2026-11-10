package com.brickarchivio.app.export

import com.brickarchivio.app.domain.ChecklistRow
import com.brickarchivio.app.domain.ChecklistSection
import com.brickarchivio.app.domain.Container
import com.brickarchivio.app.domain.ContainerEntry
import com.brickarchivio.app.domain.Exhibition
import com.brickarchivio.app.domain.ItemType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Genera un inventario di esempio (dati del modello INVENTARIO.docx dell'utente) e
 * verifica che il .docx sia uno zip valido con le parti necessarie.
 * Il file resta in app/build/sample-inventario.docx per il controllo visivo.
 */
class DocxWriterTest {

    private var nextId = 1L

    private fun manual(label: String, qty: Int = 1) =
        ContainerEntry(nextId++, 0, null, label, qty, null, null, null, null, null)

    private fun set(label: String, code: String) =
        ContainerEntry(nextId++, 0, nextId, label, 1, null, ItemType.SET, code, null, null)

    private fun section(name: String, entries: List<ContainerEntry>, out: Int = 0, ret: Int = 0) =
        ChecklistSection(
            Container(nextId++, name, null, entries),
            entries.mapIndexed { i, e -> ChecklistRow(e, i < out, i < ret) }
        )

    private fun sample(): Exhibition {
        val kit = listOf(
            manual("alberi", 10), manual("cespugli", 5), manual("Sacchetto 150 foglie"), manual("Pezzi neri"),
            manual("velociraptor", 6), manual("uova velociraptor", 2), manual("Astronauta carrozzina"),
            manual("Astronauta motovolante"), manual("Alieno con casco"), manual("Due laboratori JP"),
            manual("minifigures JP", 8), manual("Foglie Varie"), manual("Pezzi verdi"),
            manual("Sacchetto con pezzi misti"), manual("Batterie")
        )
        return Exhibition(
            1, "Allestimento dalle 8", "Lucca", "2026-10-04", null, null,
            listOf(
                section("Kit", kit, out = kit.size, ret = 12),
                section("Fucsia", listOf(set("T-rex Breakout", "76956-1"), set("Dinosauro Collolungo", "76960-1"), set("Jeep", "76960-1"), manual("Scritta")), out = 4, ret = 4),
                section("Trasparente con chiusure blu", listOf(manual("Cornice nera"), manual("Basi verdi"), manual("Centro visitatori + T-Rex + Velociraptor + Minifigures"), manual("Scheletri Triceratopo/T-Rex + Minifigure Professor Grent"), manual("Sacchetti vuoti"), set("Triceratopo + jeep + minifigures + pila di popò", "76958-1"), set("Dilofosauro + cartello + jeep + minifigures", "76958-1"), manual("Sacchetto con alberi"), manual("Kit luci"))),
                section("Trasparente con coperchio azzurro", listOf(set("Cancello", "75936-1"), set("Albero", "76960-1"))),
                section("Trasparente con coperchio verde", listOf(set("T-Rex", "75936-1"))),
                section("Fuori scatola", listOf(manual("Assi di legno"), manual("Carrello")))
            )
        )
    }

    @Test
    fun writesValidDocx() {
        val ex = sample()
        val reports = listOf(
            ReportBuilder.fromExhibition(ex, CheckMode.BLANK),
            ReportBuilder.fromExhibition(ex, CheckMode.FROM_APP)
        )
        val file = File("build/sample-inventario.docx")
        file.parentFile?.mkdirs()
        file.outputStream().use { DocxWriter.write(reports, it) }

        ZipFile(file).use { zip ->
            listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml", "word/numbering.xml")
                .forEach { assertTrue("manca $it", zip.getEntry(it) != null) }
            val doc = zip.getInputStream(zip.getEntry("word/document.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(doc.contains("INVENTARIO 04/10/2026 – Allestimento dalle 8"))
            assertTrue(doc.contains("T-rex Breakout – Set 76956"))
            assertTrue(doc.contains("10 × alberi"))
        }
    }
}
