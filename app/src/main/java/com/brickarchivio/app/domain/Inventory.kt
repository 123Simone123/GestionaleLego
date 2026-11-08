package com.brickarchivio.app.domain

import com.brickarchivio.app.data.db.ContainerWithEntries
import com.brickarchivio.app.data.db.EntryWithItem

/**
 * Riga di un contenitore. Se [itemId] non è null rimanda a un oggetto del catalogo,
 * altrimenti è una voce scritta a mano (es. pezzi sfusi).
 */
data class ContainerEntry(
    val id: Long,
    val containerId: Long,
    val itemId: Long?,
    val label: String,
    val quantity: Int,
    val notes: String?,
    val itemType: ItemType?,
    val itemCode: String?,
    val imagePath: String?,
    val imageUrl: String?
) {
    val isFromCatalog get() = itemId != null
}

data class Container(
    val id: Long,
    val name: String,
    val description: String?,
    val entries: List<ContainerEntry>
) {
    /** Numero totale di esemplari contenuti (somma delle quantità). */
    val totalQuantity get() = entries.sumOf { it.quantity }
}

/** Riga della checklist di un'esposizione. */
data class ChecklistRow(val entry: ContainerEntry, val outbound: Boolean, val returned: Boolean) {
    /** Partito ma non tornato. */
    val missing get() = outbound && !returned
}

data class ChecklistSection(val container: Container, val rows: List<ChecklistRow>) {
    val outboundCount get() = rows.count { it.outbound }
    val returnedCount get() = rows.count { it.returned }
}

data class Exhibition(
    val id: Long,
    val name: String,
    val place: String?,
    val startDate: String?,
    val endDate: String?,
    val notes: String?,
    val sections: List<ChecklistSection>
) {
    val rows get() = sections.flatMap { it.rows }
    val total get() = rows.size
    val outboundCount get() = rows.count { it.outbound }
    val returnedCount get() = rows.count { it.returned }
    val missing get() = rows.filter { it.missing }
}

fun EntryWithItem.toDomain() = ContainerEntry(
    id = entry.id,
    containerId = entry.containerId,
    itemId = entry.itemId,
    label = item?.name ?: entry.label,
    quantity = entry.quantity,
    notes = entry.notes,
    itemType = item?.let { ItemType.fromDb(it.type) },
    itemCode = item?.code,
    imagePath = item?.officialImagePath,
    imageUrl = item?.officialImageUrl
)

fun ContainerWithEntries.toDomain() = Container(
    id = container.id,
    name = container.name,
    description = container.description,
    entries = entries.sortedWith(compareBy({ it.entry.position }, { it.entry.id })).map { it.toDomain() }
)
