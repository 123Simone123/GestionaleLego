package com.brickarchivio.app.export

/**
 * Contenuto di un inventario stampabile, indipendente dal formato (Word o PDF).
 * Ricalca il modello "INVENTARIO.docx" fornito dall'utente:
 *
 *   INVENTARIO 04/10/2026 – Nome esposizione
 *   | Scatola | Contenuto (elenco puntato) | Andata | Ritorno |
 */
data class InventoryReport(
    val title: String,
    val boxes: List<ReportBox>
)

data class ReportBox(
    val name: String,
    val lines: List<String>,
    /** testo della colonna Andata: vuoto, "✓" o "3/5" */
    val outbound: String,
    /** testo della colonna Ritorno */
    val returned: String
)

/** Intestazioni e proporzioni delle colonne (in twip, come nel modello Word). */
object ReportLayout {
    val headers = listOf("Scatola", "Contenuto", "Andata", "Ritorno")
    val columnTwips = intArrayOf(1838, 5670, 1094, 1026)
    const val HEADER_FILL = "D1D1D1"
}
