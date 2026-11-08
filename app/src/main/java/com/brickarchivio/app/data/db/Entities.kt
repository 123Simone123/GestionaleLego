package com.brickarchivio.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Riga del catalogo. Un'unica tabella per i tre tipi (SET, MINIFIG, PART),
 * distinti dalla colonna [type]; il modello a oggetti vero e proprio è in
 * domain/LegoItem.kt e viene ricostruito da qui.
 *
 * (type, code) è univoco: se scansioni di nuovo lo stesso set non viene
 * duplicato, aumenta la quantità e la foto viene aggiunta a quell'oggetto.
 */
@Entity(
    tableName = "items",
    indices = [Index(value = ["type", "code"], unique = true)]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val code: String,
    val name: String,
    val year: Int? = null,
    val theme: String? = null,
    val category: String? = null,
    val numParts: Int? = null,
    val officialImageUrl: String? = null,
    val officialImagePath: String? = null,
    val rebrickableUrl: String? = null,
    val bricklinkUrl: String? = null,
    val quantity: Int = 1,
    val pricePaid: Double? = null,
    val purchaseDate: String? = null,
    val purchasePlace: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Foto scattata dall'utente (ritaglio dell'oggetto) collegata a un elemento del catalogo. */
@Entity(
    tableName = "photos",
    foreignKeys = [ForeignKey(
        entity = ItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("itemId")]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    /** Ritaglio dell'oggetto, salvato nella memoria interna dell'app. */
    val path: String,
    /** Foto intera da cui è stato ricavato il ritaglio (può essere condivisa da più oggetti). */
    val sourcePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "item_tags",
    primaryKeys = ["itemId", "tagId"],
    foreignKeys = [
        ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class ItemTagCrossRef(
    val itemId: Long,
    val tagId: Long
)

/** Elemento con foto e tag, caricato in un colpo solo da Room. */
data class ItemWithDetails(
    @Embedded val item: ItemEntity,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val photos: List<PhotoEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ItemTagCrossRef::class,
            parentColumn = "itemId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)
