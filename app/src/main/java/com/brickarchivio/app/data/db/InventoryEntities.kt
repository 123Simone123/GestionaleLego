package com.brickarchivio.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Contenitore fisico (scatola, cassa, valigia…) usato per trasportare i LEGO. */
@Entity(tableName = "containers")
data class ContainerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Riga di un contenitore: un oggetto del catalogo ([itemId]) oppure una voce scritta a mano
 * (itemId null, es. "Pezzi sfusi grigi"). [label] contiene sempre il nome: per le voci dal
 * catalogo è una copia, così la riga resta leggibile anche se l'oggetto viene eliminato
 * (in quel caso itemId diventa null).
 */
@Entity(
    tableName = "container_entries",
    foreignKeys = [
        ForeignKey(ContainerEntity::class, ["id"], ["containerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("containerId"), Index("itemId")]
)
data class ContainerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val containerId: Long,
    val itemId: Long? = null,
    val label: String,
    val quantity: Int = 1,
    val notes: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Esposizione: evento con data e luogo; ha la sua checklist andata/ritorno. */
@Entity(tableName = "exhibitions")
data class ExhibitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val place: String? = null,
    /** ISO AAAA-MM-GG */
    val startDate: String? = null,
    val endDate: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Contenitori portati a un'esposizione. */
@Entity(
    tableName = "exhibition_containers",
    primaryKeys = ["exhibitionId", "containerId"],
    foreignKeys = [
        ForeignKey(ExhibitionEntity::class, ["id"], ["exhibitionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ContainerEntity::class, ["id"], ["containerId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("containerId")]
)
data class ExhibitionContainerEntity(
    val exhibitionId: Long,
    val containerId: Long
)

/** Spunte andata/ritorno di una riga di contenitore, per una certa esposizione. */
@Entity(
    tableName = "exhibition_checks",
    primaryKeys = ["exhibitionId", "entryId"],
    foreignKeys = [
        ForeignKey(ExhibitionEntity::class, ["id"], ["exhibitionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ContainerEntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("entryId")]
)
data class ExhibitionCheckEntity(
    val exhibitionId: Long,
    val entryId: Long,
    @ColumnInfo(defaultValue = "0") val outbound: Boolean = false,
    @ColumnInfo(defaultValue = "0") val returned: Boolean = false
)

/** Riga con il relativo oggetto del catalogo (se c'è), per mostrare immagine e codice. */
data class EntryWithItem(
    @Embedded val entry: ContainerEntryEntity,
    @Relation(parentColumn = "itemId", entityColumn = "id")
    val item: ItemEntity?
)

data class ContainerWithEntries(
    @Embedded val container: ContainerEntity,
    @Relation(entity = ContainerEntryEntity::class, parentColumn = "id", entityColumn = "containerId")
    val entries: List<EntryWithItem>
)

/** Dove si trova un oggetto del catalogo. */
data class ItemLocation(
    val containerId: Long,
    val containerName: String,
    val quantity: Int
)

/** Riepilogo di un'esposizione per l'elenco. */
data class ExhibitionSummary(
    @Embedded val exhibition: ExhibitionEntity,
    val containerCount: Int,
    val entryCount: Int,
    val outboundCount: Int,
    val returnedCount: Int,
    val missingCount: Int
)
