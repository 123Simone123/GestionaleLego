package com.brickarchivio.app.export

import com.brickarchivio.app.domain.ChecklistSection
import com.brickarchivio.app.domain.ContainerEntry
import com.brickarchivio.app.domain.Exhibition
import com.brickarchivio.app.domain.ItemType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Cosa scrivere nelle colonne Andata e Ritorno. */
enum class CheckMode(val label: String) {
    /** colonne vuote, da spuntare a penna (come il modello) */
    BLANK("Vuote, da spuntare a mano"),
    /** ✓ se tutte le righe del contenitore sono spuntate nell'app, altrimenti "x/y" */
    FROM_APP("Con le spunte dell'app")
}

object ReportBuilder {

    private val itDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun fromExhibition(ex: Exhibition, mode: CheckMode): InventoryReport =
        InventoryReport(title = title(ex), boxes = ex.sections.map { box(it, mode) })

    /** "INVENTARIO 04/10/2026 – Nome esposizione" */
    fun title(ex: Exhibition): String {
        val date = ex.startDate?.let { runCatching { LocalDate.parse(it).format(itDate) }.getOrNull() }
        return listOfNotNull("INVENTARIO" + (date?.let { " $it" } ?: ""), ex.name.ifBlank { null })
            .joinToString(" – ")
    }

    private fun box(section: ChecklistSection, mode: CheckMode): ReportBox {
        val rows = section.rows
        fun column(done: Int) = when {
            mode == CheckMode.BLANK || rows.isEmpty() -> ""
            done == rows.size -> "✓"
            done == 0 -> ""
            else -> "$done/${rows.size}"
        }
        return ReportBox(
            name = section.container.name,
            lines = rows.map { line(it.entry) },
            outbound = column(rows.count { it.outbound }),
            returned = column(rows.count { it.returned })
        )
    }

    /**
     * Riga del contenuto, nello stile del modello:
     *   "T-rex Breakout – Set 76956", "3 × Velociraptor", "Pezzi verdi (sacchetto)".
     */
    fun line(e: ContainerEntry): String {
        val base = if (e.itemType == ItemType.SET && e.itemCode != null) {
            "${e.label} – Set ${e.itemCode.removeSuffix("-1")}"
        } else e.label
        val withQty = if (e.quantity > 1) "${e.quantity} × $base" else base
        return if (e.notes.isNullOrBlank()) withQty else "$withQty (${e.notes})"
    }
}
