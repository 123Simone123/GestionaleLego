package com.brickarchivio.app.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brickarchivio.app.domain.ChecklistRow
import com.brickarchivio.app.domain.ChecklistSection
import com.brickarchivio.app.domain.Container
import com.brickarchivio.app.domain.ContainerEntry
import com.brickarchivio.app.domain.Exhibition
import com.brickarchivio.app.domain.ItemType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Genera un PDF di esempio sul dispositivo (files/sample-inventario.pdf) per il
 * controllo visivo dell'impaginazione. Usato dal workflow "PDF check".
 */
@RunWith(AndroidJUnit4::class)
class PdfWriterTest {

    private var nextId = 1L
    private fun manual(label: String, qty: Int = 1) =
        ContainerEntry(nextId++, 0, null, label, qty, null, null, null, null, null)
    private fun set(label: String, code: String) =
        ContainerEntry(nextId++, 0, nextId, label, 1, null, ItemType.SET, code, null, null)
    private fun section(name: String, entries: List<ContainerEntry>, out: Int = 0, ret: Int = 0) =
        ChecklistSection(Container(nextId++, name, null, entries), entries.mapIndexed { i, e -> ChecklistRow(e, i < out, i < ret) })

    @Test
    fun writesPdf() {
        val kit = listOf("alberi" to 10, "cespugli" to 5, "Sacchetto 150 foglie" to 1, "Pezzi neri" to 1,
            "velociraptor" to 6, "uova velociraptor" to 2, "Astronauta carrozzina" to 1, "Astronauta motovolante" to 1,
            "Alieno con casco" to 1, "Due laboratori JP" to 1, "minifigures JP" to 8, "Foglie Varie" to 1,
            "Pezzi verdi" to 1, "Sacchetto con pezzi misti" to 1, "Batterie" to 1).map { manual(it.first, it.second) }
        val long = (1..45).map { manual("Voce lunga di prova numero $it per verificare il salto pagina") }
        val ex = Exhibition(
            1, "Allestimento dalle 8", "Lucca", "2026-10-04", null, null,
            listOf(
                section("Kit", kit, out = kit.size, ret = 12),
                section("Fucsia", listOf(set("T-rex Breakout", "76956-1"), set("Dinosauro Collolungo", "76960-1"), set("Jeep", "76960-1"), manual("Scritta")), 4, 4),
                section("Trasparente con chiusure blu", listOf(manual("Cornice nera"), manual("Basi verdi"), manual("Centro visitatori + T-Rex + Velociraptor + Minifigures"), set("Triceratopo + jeep + minifigures + pila di popò", "76958-1"))),
                section("Cassa molto piena", long),
                section("Fuori scatola", listOf(manual("Assi di legno"), manual("Carrello")))
            )
        )
        val reports = listOf(ReportBuilder.fromExhibition(ex, CheckMode.BLANK), ReportBuilder.fromExhibition(ex, CheckMode.FROM_APP))
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(ctx.filesDir, "sample-inventario.pdf")
        file.outputStream().use { PdfWriter.write(reports, it) }
        assertTrue(file.length() > 1000)
        // copia anche nella cartella esterna dell'app, leggibile con adb pull
        ctx.getExternalFilesDir(null)?.let { dir -> file.copyTo(File(dir, file.name), overwrite = true) }
    }
}
