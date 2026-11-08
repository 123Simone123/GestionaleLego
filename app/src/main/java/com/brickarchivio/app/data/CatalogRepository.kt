package com.brickarchivio.app.data

import android.net.Uri
import com.brickarchivio.app.data.db.CatalogDao
import com.brickarchivio.app.data.db.ItemEntity
import com.brickarchivio.app.data.db.ItemTagCrossRef
import com.brickarchivio.app.data.db.PhotoEntity
import com.brickarchivio.app.data.db.TagEntity
import com.brickarchivio.app.data.remote.Candidate
import com.brickarchivio.app.data.remote.Http
import com.brickarchivio.app.data.remote.RebrickableClient
import com.brickarchivio.app.domain.ItemType
import com.brickarchivio.app.domain.LegoItem
import com.brickarchivio.app.domain.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Unico punto di accesso al catalogo per la UI.
 * Coordina database, file sul telefono e servizi online.
 */
class CatalogRepository(
    private val dao: CatalogDao,
    private val storage: PhotoStorage,
    private val rebrickable: RebrickableClient
) {

    fun observeItems(): Flow<List<LegoItem>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeItem(id: Long): Flow<LegoItem?> = dao.observeItem(id).map { it?.toDomain() }

    fun observeTagNames(): Flow<List<String>> = dao.observeTags().map { tags -> tags.map { it.name } }

    data class AddResult(val itemId: Long, val created: Boolean, val warning: String?)

    /**
     * Aggiunge al catalogo l'oggetto confermato dall'utente.
     * - completa i dati da Rebrickable (se c'è la chiave);
     * - scarica l'immagine ufficiale sul telefono;
     * - se l'oggetto esiste già aumenta la quantità invece di duplicarlo;
     * - collega il ritaglio della foto all'oggetto.
     */
    suspend fun addFromCandidate(
        candidate: Candidate,
        cropPath: String?,
        sourcePath: String?
    ): AddResult {
        val (data, warning) = enrich(candidate)
        val existing = dao.findByCode(data.type.name, data.code)
        val now = System.currentTimeMillis()
        val itemId: Long
        val created: Boolean
        if (existing != null) {
            dao.updateItem(existing.copy(quantity = existing.quantity + 1, updatedAt = now))
            itemId = existing.id
            created = false
        } else {
            val imagePath = downloadOfficialImage(data)
            itemId = dao.insertItem(data.toEntity(imagePath, now))
            created = true
        }
        if (cropPath != null) {
            dao.insertPhoto(PhotoEntity(itemId = itemId, path = cropPath, sourcePath = sourcePath))
        }
        return AddResult(itemId, created, warning)
    }

    /** Aggiunge una foto (dalla galleria/fotocamera) a un oggetto esistente. */
    suspend fun addPhoto(itemId: Long, uri: Uri): Boolean {
        val path = storage.importImage(uri) ?: return false
        dao.insertPhoto(PhotoEntity(itemId = itemId, path = path, sourcePath = null))
        touch(itemId)
        return true
    }

    suspend fun deletePhoto(photoId: Long) {
        val photo = dao.getPhoto(photoId) ?: return
        dao.deletePhoto(photoId)
        storage.deleteFile(photo.path)
        deleteSourceIfUnused(photo.sourcePath)
    }

    suspend fun deleteItem(id: Long) {
        val item = dao.getItem(id) ?: return
        val photos = dao.photosOf(id)
        dao.deleteItem(id) // le foto e i tag collegati vengono cancellati in cascata
        photos.forEach {
            storage.deleteFile(it.path)
            deleteSourceIfUnused(it.sourcePath)
        }
        storage.deleteFile(item.officialImagePath)
        dao.deleteUnusedTags()
    }

    suspend fun updatePersonal(
        id: Long,
        quantity: Int,
        pricePaid: Double?,
        purchaseDate: String?,
        purchasePlace: String?,
        notes: String?
    ) {
        val item = dao.getItem(id) ?: return
        dao.updateItem(
            item.copy(
                quantity = quantity.coerceAtLeast(1),
                pricePaid = pricePaid,
                purchaseDate = purchaseDate?.ifBlank { null },
                purchasePlace = purchasePlace?.ifBlank { null },
                notes = notes?.ifBlank { null },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addTag(itemId: Long, rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        val tagId = dao.findTag(name)?.id ?: dao.insertTag(TagEntity(name = name)).let { id ->
            if (id > 0) id else dao.findTag(name)?.id ?: return
        }
        dao.addTagToItem(ItemTagCrossRef(itemId, tagId))
        touch(itemId)
    }

    suspend fun removeTag(itemId: Long, name: String) {
        val tag = dao.findTag(name) ?: return
        dao.removeTagFromItem(itemId, tag.id)
        dao.deleteUnusedTags()
        touch(itemId)
    }

    /** Riscarica i dati ufficiali (utile dopo aver inserito la chiave Rebrickable). */
    suspend fun refreshOfficialData(id: Long): String? {
        val item = dao.getItem(id) ?: return "Elemento non trovato"
        val base = Candidate(
            type = ItemType.fromDb(item.type), code = item.code, name = item.name,
            imageUrl = item.officialImageUrl, year = item.year, theme = item.theme,
            category = item.category, numParts = item.numParts,
            rebrickableUrl = item.rebrickableUrl, bricklinkUrl = item.bricklinkUrl
        )
        val (data, warning) = enrich(base)
        val imagePath = downloadOfficialImage(data) ?: item.officialImagePath
        dao.updateItem(
            item.copy(
                name = data.name, year = data.year, theme = data.theme, category = data.category,
                numParts = data.numParts, officialImageUrl = data.imageUrl,
                officialImagePath = imagePath, rebrickableUrl = data.rebrickableUrl,
                bricklinkUrl = data.bricklinkUrl, updatedAt = System.currentTimeMillis()
            )
        )
        return warning
    }

    // Ricerca manuale -----------------------------------------------------------------

    suspend fun search(type: ItemType, query: String): List<Candidate> = when (type) {
        ItemType.SET -> {
            val q = query.trim()
            val direct = if (q.matches(Regex("\\d{3,7}(-\\d+)?"))) {
                rebrickable.getSet(if (q.contains('-')) q else "$q-1")
            } else null
            (listOfNotNull(direct) + rebrickable.searchSets(q)).distinctBy { it.code }
        }
        ItemType.MINIFIG -> {
            val q = query.trim()
            val direct = if (q.startsWith("fig-")) rebrickable.getMinifig(q) else null
            (listOfNotNull(direct) + rebrickable.searchMinifigs(q)).distinctBy { it.code }
        }
        ItemType.PART -> {
            val q = query.trim()
            val direct = runCatching { rebrickable.getPart(q) }.getOrNull()
            (listOfNotNull(direct) + rebrickable.searchParts(q)).distinctBy { it.code }
        }
    }

    // ---------------------------------------------------------------------------------

    /** Completa i dati con Rebrickable; se non è possibile tiene quelli del riconoscimento. */
    private suspend fun enrich(c: Candidate): Pair<Candidate, String?> {
        if (!rebrickable.hasKey()) {
            return c to "Senza chiave Rebrickable sono salvati solo i dati del riconoscimento"
        }
        return try {
            val full = when (c.type) {
                ItemType.SET -> rebrickable.getSet(c.code)
                ItemType.PART -> rebrickable.getPart(c.code)
                // Brickognize usa i codici BrickLink (es. sw0001); Rebrickable usa fig-xxxxxx
                ItemType.MINIFIG -> if (c.code.startsWith("fig-")) rebrickable.getMinifig(c.code) else null
            }
            if (full == null) {
                c to null
            } else {
                full.copy(
                    name = full.name.ifBlank { c.name },
                    imageUrl = full.imageUrl ?: c.imageUrl,
                    year = full.year ?: c.year,
                    theme = full.theme ?: c.theme,
                    category = full.category ?: c.category,
                    numParts = full.numParts ?: c.numParts,
                    rebrickableUrl = full.rebrickableUrl ?: c.rebrickableUrl,
                    bricklinkUrl = c.bricklinkUrl ?: full.bricklinkUrl
                ) to null
            }
        } catch (e: Exception) {
            c to "Dati Rebrickable non disponibili: ${e.message}"
        }
    }

    private suspend fun downloadOfficialImage(c: Candidate): String? {
        val url = c.imageUrl ?: return null
        val file = storage.officialFileFor(c.type.name, c.code)
        return if (Http.download(url, file)) file.absolutePath else null
    }

    private suspend fun deleteSourceIfUnused(sourcePath: String?) {
        if (sourcePath != null && dao.countPhotosWithSource(sourcePath) == 0) {
            storage.deleteFile(sourcePath)
        }
    }

    private suspend fun touch(itemId: Long) {
        dao.getItem(itemId)?.let { dao.updateItem(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    private fun Candidate.toEntity(imagePath: String?, now: Long) = ItemEntity(
        type = type.name,
        code = code,
        name = name,
        year = year,
        theme = theme,
        category = category,
        numParts = numParts,
        officialImageUrl = imageUrl,
        officialImagePath = imagePath,
        rebrickableUrl = rebrickableUrl,
        bricklinkUrl = bricklinkUrl,
        quantity = 1,
        createdAt = now,
        updatedAt = now
    )
}
