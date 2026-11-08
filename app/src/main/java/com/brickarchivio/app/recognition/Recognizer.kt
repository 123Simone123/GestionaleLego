package com.brickarchivio.app.recognition

import android.graphics.Bitmap
import com.brickarchivio.app.data.NormRect
import com.brickarchivio.app.data.PhotoStorage
import com.brickarchivio.app.data.remote.BrickognizeClient
import com.brickarchivio.app.data.remote.Candidate
import com.brickarchivio.app.data.remote.RebrickableClient
import com.brickarchivio.app.domain.ItemType
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pipeline di riconoscimento.
 *
 * 1. [detectObjects]: ML Kit Object Detection (sul telefono, offline) trova fino a
 *    5 oggetti nella foto e propone i riquadri; l'utente può correggerli.
 * 2. [recognize] su ogni ritaglio:
 *    a. ML Kit Text Recognition (offline) legge i numeri stampati: se è una scatola
 *       il numero del set viene verificato su Rebrickable;
 *    b. il ritaglio viene inviato a Brickognize, che riconosce set, minifigure e pezzi;
 *    c. i risultati vengono uniti e ordinati.
 */
class Recognizer(
    private val storage: PhotoStorage,
    private val brickognize: BrickognizeClient,
    private val rebrickable: RebrickableClient
) {

    data class RegionRecognition(
        val candidates: List<Candidate>,
        val numbersRead: List<String>,
        val brickognizeRaw: String?,
        val errors: List<String>
    )

    suspend fun detectObjects(bitmap: Bitmap): List<NormRect> {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build()
        val detector = ObjectDetection.getClient(options)
        return try {
            val objects = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
            objects.map {
                val b = it.boundingBox
                // piccolo margine attorno all'oggetto
                val padX = b.width() * 0.05f
                val padY = b.height() * 0.05f
                NormRect(
                    (b.left - padX) / bitmap.width,
                    (b.top - padY) / bitmap.height,
                    (b.right + padX) / bitmap.width,
                    (b.bottom + padY) / bitmap.height
                ).normalized()
            }.filter { it.width > 0.03f && it.height > 0.03f }
        } catch (e: Exception) {
            emptyList()
        } finally {
            detector.close()
        }
    }

    suspend fun recognize(crop: Bitmap): RegionRecognition {
        val errors = mutableListOf<String>()

        // a. numeri letti offline (scatole)
        val numbers = try {
            readSetNumbers(crop)
        } catch (e: Exception) {
            errors += "Lettura testo: ${e.message}"
            emptyList()
        }

        // b. Brickognize
        var raw: String? = null
        val visual = try {
            val result = brickognize.predict(storage.toUploadJpeg(crop))
            raw = result.raw
            result.candidates
        } catch (e: Exception) {
            errors += e.message ?: "Errore Brickognize"
            emptyList()
        }

        // a-bis. verifica dei numeri letti su Rebrickable (max 3 per rispettare il limite di 1 richiesta/s)
        val fromBox = mutableListOf<Candidate>()
        if (numbers.isNotEmpty() && rebrickable.hasKey()) {
            val visualSetCodes = visual.filter { it.type == ItemType.SET }.map { it.code }.toSet()
            // prima i numeri che anche Brickognize ha proposto
            val ordered = numbers.sortedByDescending { "$it-1" in visualSetCodes }
            for (n in ordered.take(3)) {
                try {
                    rebrickable.getSet("$n-1")?.let {
                        fromBox += it.copy(score = 0.99, source = "Numero letto sulla scatola")
                    }
                } catch (e: Exception) {
                    errors += e.message ?: "Errore Rebrickable"
                    break
                }
            }
        }

        // c. unione senza duplicati
        val merged = LinkedHashMap<String, Candidate>()
        (fromBox + visual.sortedByDescending { it.score ?: 0.0 }).forEach { c ->
            val key = "${c.type}|${c.code}"
            val existing = merged[key]
            if (existing == null) {
                merged[key] = c
            } else if (existing.imageUrl == null && c.imageUrl != null) {
                merged[key] = existing.copy(imageUrl = c.imageUrl, bricklinkUrl = existing.bricklinkUrl ?: c.bricklinkUrl)
            }
        }
        return RegionRecognition(merged.values.take(8), numbers, raw, errors)
    }

    /**
     * Legge i numeri da 4 a 7 cifre (formato dei numeri di set) e li ordina per
     * altezza del testo: sulle scatole il numero del set è stampato grande.
     */
    private suspend fun readSetNumbers(bitmap: Bitmap): List<String> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val regex = Regex("(?<!\\d)(\\d{4,7})(?:-\\d{1,2})?(?!\\d)")
            val found = mutableListOf<Pair<String, Int>>()
            for (block in text.textBlocks) for (line in block.lines) {
                val h = line.boundingBox?.height() ?: 0
                regex.findAll(line.text).forEach { m ->
                    found += m.groupValues[1] to h
                }
            }
            return found.sortedByDescending { it.second }.map { it.first }.distinct()
        } finally {
            recognizer.close()
        }
    }
}

/** Attende un Task di Google Play Services in una coroutine. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
