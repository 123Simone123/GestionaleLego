package com.brickarchivio.app.data.remote

import com.brickarchivio.app.domain.ItemType

/**
 * Possibile identificazione di un oggetto (da Brickognize, dalla lettura del numero
 * sulla scatola o da una ricerca manuale). Diventa un elemento del catalogo solo
 * quando l'utente lo conferma.
 */
data class Candidate(
    val type: ItemType,
    val code: String,
    val name: String,
    val imageUrl: String? = null,
    val year: Int? = null,
    val theme: String? = null,
    val category: String? = null,
    val numParts: Int? = null,
    val rebrickableUrl: String? = null,
    val bricklinkUrl: String? = null,
    /** Affidabilità 0..1 (solo indicativa). */
    val score: Double? = null,
    /** Da dove arriva il suggerimento, mostrato all'utente. */
    val source: String = ""
)
