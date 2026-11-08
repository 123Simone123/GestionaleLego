package com.brickarchivio.app.data

import com.brickarchivio.app.data.db.ContainerEntity
import com.brickarchivio.app.data.db.ContainerEntryEntity
import com.brickarchivio.app.data.db.ExhibitionCheckEntity
import com.brickarchivio.app.data.db.ExhibitionContainerEntity
import com.brickarchivio.app.data.db.ExhibitionEntity
import com.brickarchivio.app.data.db.ExhibitionSummary
import com.brickarchivio.app.data.db.InventoryDao
import com.brickarchivio.app.data.db.ItemLocation
import com.brickarchivio.app.domain.ChecklistRow
import com.brickarchivio.app.domain.ChecklistSection
import com.brickarchivio.app.domain.Container
import com.brickarchivio.app.domain.Exhibition
import com.brickarchivio.app.domain.LegoItem
import com.brickarchivio.app.domain.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Contenitori, righe ed esposizioni (andata/ritorno). */
class InventoryRepository(private val dao: InventoryDao) {

    // --- Contenitori --------------------------------------------------------------

    fun observeContainers(): Flow<List<Container>> = dao.observeContainers().map { l -> l.map { it.toDomain() } }

    fun observeContainer(id: Long): Flow<Container?> = dao.observeContainer(id).map { it?.toDomain() }

    fun observeLocations(itemId: Long): Flow<List<ItemLocation>> = dao.observeLocations(itemId)

    suspend fun createContainer(name: String, description: String?): Long =
        dao.insertContainer(ContainerEntity(name = name.trim(), description = description?.trim()?.ifBlank { null }))

    suspend fun updateContainer(id: Long, name: String, description: String?) {
        val c = dao.getContainer(id) ?: return
        dao.updateContainer(
            c.copy(name = name.trim(), description = description?.trim()?.ifBlank { null }, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteContainer(id: Long) = dao.deleteContainer(id)

    /** Aggiunge un oggetto del catalogo; se è già nel contenitore ne somma la quantità. */
    suspend fun addCatalogItem(containerId: Long, item: LegoItem, quantity: Int) {
        val existing = dao.findEntryForItem(containerId, item.id)
        if (existing != null) {
            dao.updateEntry(existing.copy(quantity = existing.quantity + quantity))
        } else {
            dao.insertEntry(
                ContainerEntryEntity(
                    containerId = containerId,
                    itemId = item.id,
                    label = item.name,
                    quantity = quantity.coerceAtLeast(1),
                    position = dao.maxPosition(containerId) + 1
                )
            )
        }
        touch(containerId)
    }

    /** Aggiunge una voce scritta a mano (es. "Pezzi sfusi grigi"). */
    suspend fun addManualEntry(containerId: Long, label: String, quantity: Int, notes: String?) {
        dao.insertEntry(
            ContainerEntryEntity(
                containerId = containerId,
                itemId = null,
                label = label.trim(),
                quantity = quantity.coerceAtLeast(1),
                notes = notes?.trim()?.ifBlank { null },
                position = dao.maxPosition(containerId) + 1
            )
        )
        touch(containerId)
    }

    suspend fun updateEntry(entryId: Long, label: String?, quantity: Int, notes: String?) {
        val e = dao.getEntry(entryId) ?: return
        dao.updateEntry(
            e.copy(
                label = label?.trim()?.ifBlank { null } ?: e.label,
                quantity = quantity.coerceAtLeast(1),
                notes = notes?.trim()?.ifBlank { null }
            )
        )
        touch(e.containerId)
    }

    suspend fun deleteEntry(entryId: Long) {
        val e = dao.getEntry(entryId) ?: return
        dao.deleteEntry(entryId)
        touch(e.containerId)
    }

    private suspend fun touch(containerId: Long) {
        dao.getContainer(containerId)?.let { dao.updateContainer(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    // --- Esposizioni ----------------------------------------------------------------

    fun observeExhibitionSummaries(): Flow<List<ExhibitionSummary>> = dao.observeExhibitionSummaries()

    /** Esposizione con la checklist: contenitori portati, loro righe e spunte. */
    fun observeExhibition(id: Long): Flow<Exhibition?> = combine(
        dao.observeExhibition(id),
        dao.observeExhibitionContainerIds(id),
        observeContainers(),
        dao.observeChecks(id)
    ) { ex, containerIds, containers, checks ->
        if (ex == null) return@combine null
        val byEntry = checks.associateBy { it.entryId }
        val sections = containers.filter { it.id in containerIds }.map { c ->
            ChecklistSection(c, c.entries.map { e ->
                val k = byEntry[e.id]
                ChecklistRow(e, outbound = k?.outbound == true, returned = k?.returned == true)
            })
        }
        Exhibition(ex.id, ex.name, ex.place, ex.startDate, ex.endDate, ex.notes, sections)
    }

    suspend fun createExhibition(name: String, place: String?, startDate: String?, endDate: String?): Long =
        dao.insertExhibition(
            ExhibitionEntity(
                name = name.trim(),
                place = place?.trim()?.ifBlank { null },
                startDate = startDate?.ifBlank { null },
                endDate = endDate?.ifBlank { null }
            )
        )

    suspend fun updateExhibition(id: Long, name: String, place: String?, startDate: String?, endDate: String?, notes: String?) {
        val e = dao.getExhibition(id) ?: return
        dao.updateExhibition(
            e.copy(
                name = name.trim(),
                place = place?.trim()?.ifBlank { null },
                startDate = startDate?.ifBlank { null },
                endDate = endDate?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null }
            )
        )
    }

    suspend fun deleteExhibition(id: Long) = dao.deleteExhibition(id)

    suspend fun addContainerToExhibition(exhibitionId: Long, containerId: Long) =
        dao.addExhibitionContainer(ExhibitionContainerEntity(exhibitionId, containerId))

    suspend fun removeContainerFromExhibition(exhibitionId: Long, containerId: Long) =
        dao.removeExhibitionContainer(exhibitionId, containerId)

    suspend fun setOutbound(exhibitionId: Long, entryId: Long, value: Boolean) {
        val c = dao.getCheck(exhibitionId, entryId) ?: ExhibitionCheckEntity(exhibitionId, entryId)
        dao.upsertCheck(c.copy(outbound = value))
    }

    suspend fun setReturned(exhibitionId: Long, entryId: Long, value: Boolean) {
        val c = dao.getCheck(exhibitionId, entryId) ?: ExhibitionCheckEntity(exhibitionId, entryId)
        dao.upsertCheck(c.copy(returned = value))
    }

    /** Spunta (o toglie) andata/ritorno per tutte le righe di un contenitore. */
    suspend fun setAll(exhibitionId: Long, containerId: Long, outbound: Boolean?, returned: Boolean?) {
        for (entryId in dao.entryIdsOf(containerId)) {
            val c = dao.getCheck(exhibitionId, entryId) ?: ExhibitionCheckEntity(exhibitionId, entryId)
            dao.upsertCheck(c.copy(outbound = outbound ?: c.outbound, returned = returned ?: c.returned))
        }
    }
}
