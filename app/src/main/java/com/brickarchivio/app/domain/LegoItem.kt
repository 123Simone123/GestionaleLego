package com.brickarchivio.app.domain

import com.brickarchivio.app.data.db.ItemWithDetails

/** Tipo di oggetto del catalogo. Il nome (SET, MINIFIG, PART) è quello salvato nel database. */
enum class ItemType(val label: String) {
    SET("Set"),
    MINIFIG("Minifigure"),
    PART("Pezzo");

    companion object {
        fun fromDb(value: String): ItemType = entries.firstOrNull { it.name == value } ?: PART
    }
}

/** Immagine ufficiale: URL d'origine e copia scaricata sul telefono (usata per prima, funziona offline). */
data class OfficialImage(val url: String?, val localPath: String?)

/** Dati personali che l'utente inserisce: prezzi, note, tag. */
data class PersonalInfo(
    val quantity: Int,
    val pricePaid: Double?,
    val purchaseDate: String?,
    val purchasePlace: String?,
    val notes: String?,
    val tags: List<String>
)

/** Foto scattata dall'utente. */
data class UserPhoto(val id: Long, val path: String, val sourcePath: String?, val createdAt: Long)

/** Link a siti esterni (aperti nel browser). */
data class ExternalLinks(val rebrickable: String?, val bricklink: String?)

/**
 * Modello a oggetti del catalogo.
 *
 * Ogni oggetto contiene:
 *  - i dati ufficiali (codice, nome, immagine originale, ...),
 *  - le foto scattate dall'utente,
 *  - i dati personali (quantità, prezzo, note, tag).
 *
 * Le sottoclassi aggiungono i campi specifici del tipo.
 */
sealed class LegoItem {
    abstract val id: Long
    abstract val code: String
    abstract val name: String
    abstract val officialImage: OfficialImage
    abstract val links: ExternalLinks
    abstract val personal: PersonalInfo
    abstract val photos: List<UserPhoto>
    abstract val createdAt: Long
    abstract val type: ItemType

    /** Set ufficiale (es. 75192-1). */
    data class LegoSet(
        override val id: Long,
        override val code: String,
        override val name: String,
        val year: Int?,
        val theme: String?,
        val numParts: Int?,
        override val officialImage: OfficialImage,
        override val links: ExternalLinks,
        override val personal: PersonalInfo,
        override val photos: List<UserPhoto>,
        override val createdAt: Long
    ) : LegoItem() {
        override val type = ItemType.SET
    }

    /** Minifigure (codice BrickLink, es. sw0001, o Rebrickable fig-xxxxxx). */
    data class Minifigure(
        override val id: Long,
        override val code: String,
        override val name: String,
        val year: Int?,
        val theme: String?,
        val numParts: Int?,
        override val officialImage: OfficialImage,
        override val links: ExternalLinks,
        override val personal: PersonalInfo,
        override val photos: List<UserPhoto>,
        override val createdAt: Long
    ) : LegoItem() {
        override val type = ItemType.MINIFIG
    }

    /** Pezzo sciolto (es. 3001). */
    data class Part(
        override val id: Long,
        override val code: String,
        override val name: String,
        val category: String?,
        override val officialImage: OfficialImage,
        override val links: ExternalLinks,
        override val personal: PersonalInfo,
        override val photos: List<UserPhoto>,
        override val createdAt: Long
    ) : LegoItem() {
        override val type = ItemType.PART
    }
}

/** Ricostruisce l'oggetto di dominio dalla riga del database. */
fun ItemWithDetails.toDomain(): LegoItem {
    val e = item
    val image = OfficialImage(e.officialImageUrl, e.officialImagePath)
    val links = ExternalLinks(e.rebrickableUrl, e.bricklinkUrl)
    val personal = PersonalInfo(
        quantity = e.quantity,
        pricePaid = e.pricePaid,
        purchaseDate = e.purchaseDate,
        purchasePlace = e.purchasePlace,
        notes = e.notes,
        tags = tags.map { it.name }.sorted()
    )
    val userPhotos = photos.sortedBy { it.createdAt }
        .map { UserPhoto(it.id, it.path, it.sourcePath, it.createdAt) }
    return when (ItemType.fromDb(e.type)) {
        ItemType.SET -> LegoItem.LegoSet(
            e.id, e.code, e.name, e.year, e.theme, e.numParts,
            image, links, personal, userPhotos, e.createdAt
        )
        ItemType.MINIFIG -> LegoItem.Minifigure(
            e.id, e.code, e.name, e.year, e.theme, e.numParts,
            image, links, personal, userPhotos, e.createdAt
        )
        ItemType.PART -> LegoItem.Part(
            e.id, e.code, e.name, e.category,
            image, links, personal, userPhotos, e.createdAt
        )
    }
}

/** Testo secondario mostrato nelle liste. */
fun LegoItem.subtitle(): String = when (this) {
    is LegoItem.LegoSet -> listOfNotNull(
        code, theme, year?.toString(), numParts?.let { "$it pezzi" }
    ).joinToString(" · ")
    is LegoItem.Minifigure -> listOfNotNull(code, theme, year?.toString()).joinToString(" · ")
    is LegoItem.Part -> listOfNotNull(code, category).joinToString(" · ")
}
