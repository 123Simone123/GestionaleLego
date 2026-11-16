package com.cartefedelta.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Una tessera fedeltà salvata sul telefono. */
@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Contenuto del codice (quello che legge la cassa). */
    val number: String,
    /** Nome del formato ZXing (EAN_13, CODE_128, QR_CODE, ...). */
    val format: String,
    /** Colore della tessera (ARGB). */
    val color: Int,
    /** Chiave del negozio preimpostato, se scelto dalla lista. */
    val storeKey: String? = null,
    val note: String = "",
    /** Numero da mostrare sotto il codice se diverso dal contenuto (facoltativo). */
    val displayNumber: String = "",
    val favorite: Boolean = false,
    val frontPhoto: String? = null,
    val backPhoto: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0,
    val useCount: Int = 0
)
