package com.brickarchivio.app.ui.scan

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brickarchivio.app.BrickApp
import com.brickarchivio.app.data.NormRect
import com.brickarchivio.app.data.db.OwnedItem
import com.brickarchivio.app.data.remote.BrickognizeClient
import com.brickarchivio.app.data.remote.Candidate
import com.brickarchivio.app.recognition.Recognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanStep { PICK, BUSY, EDIT_REGIONS, REVIEW, DONE }

data class RegionUi(val id: Int, val rect: NormRect)

data class ResultUi(
    val regionId: Int,
    val cropPath: String,
    val candidates: List<Candidate>,
    /** indice del candidato scelto, -1 = non aggiungere */
    val selected: Int,
    val numbersRead: List<String>,
    val raw: String?,
    val errors: List<String>
)

/** Chiave per riconoscere i doppioni: "TIPO|codice". */
fun Candidate.key(): String = "${type.name}|$code"

/** Oggetto scelto che è già in catalogo (o ripetuto nella stessa foto): l'utente decide. */
data class DuplicateUi(
    val regionId: Int,
    val name: String,
    val code: String,
    /** quantità già in catalogo (0 se non c'è) */
    val ownedQuantity: Int,
    /** stesso oggetto già scelto in un altro riquadro di questa foto */
    val repeatedInPhoto: Boolean,
    /** true = aggiungi comunque (quantità +1), false = salta */
    val add: Boolean = false
)

data class SavedUi(val itemId: Long, val name: String, val created: Boolean, val warning: String?)

data class ScanState(
    val step: ScanStep = ScanStep.PICK,
    val busyMessage: String = "",
    val queue: List<String> = emptyList(),
    val index: Int = 0,
    val bitmap: Bitmap? = null,
    val regions: List<RegionUi> = emptyList(),
    val selectedRegion: Int? = null,
    val results: List<ResultUi> = emptyList(),
    val saved: List<SavedUi> = emptyList(),
    val message: String? = null,
    val mode: BrickognizeClient.Mode = BrickognizeClient.Mode.ALL,
    val detectMethod: Recognizer.DetectMethod = Recognizer.DetectMethod.AUTO,
    /** metodo effettivamente usato per i riquadri proposti */
    val detectUsed: Recognizer.DetectMethod? = null,
    /** già in catalogo, per "TIPO|codice" */
    val owned: Map<String, OwnedItem> = emptyMap(),
    /** non null = dialogo doppioni aperto */
    val duplicates: List<DuplicateUi>? = null
)

/**
 * Gestisce l'intero flusso di scansione:
 * PICK (scatti/galleria) → per ogni foto: EDIT_REGIONS → REVIEW → salvataggio → DONE.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BrickApp
    private val storage = app.storage
    private val recognizer = app.recognizer
    private val repo = app.repository

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    private var nextRegionId = 1
    var pendingCaptureUri: Uri? = null
        private set

    /** File creati durante la scansione e non ancora collegati a un oggetto: eliminati se si abbandona. */
    private val pendingFiles = mutableSetOf<String>()

    // --- PICK -------------------------------------------------------------------------

    fun newCaptureUri(): Uri = storage.newCaptureUri().also { pendingCaptureUri = it }

    fun onCaptured(success: Boolean) {
        val uri = pendingCaptureUri ?: return
        pendingCaptureUri = null
        if (success) importUris(listOf(uri))
    }

    fun onPicked(uris: List<Uri>) {
        if (uris.isNotEmpty()) importUris(uris)
    }

    private fun importUris(uris: List<Uri>) = viewModelScope.launch {
        _state.update { it.copy(step = ScanStep.BUSY, busyMessage = "Salvo le foto nell'app…") }
        val paths = uris.mapNotNull { storage.importImage(it) }
        pendingFiles += paths
        storage.clearCaptures()
        _state.update {
            it.copy(
                step = ScanStep.PICK,
                queue = it.queue + paths,
                message = if (paths.size < uris.size) "Alcune immagini non sono leggibili" else null
            )
        }
    }

    fun removeFromQueue(path: String) {
        storage.deleteFile(path)
        pendingFiles -= path
        _state.update { it.copy(queue = it.queue - path) }
    }

    fun startProcessing() {
        if (_state.value.queue.isNotEmpty()) openPhoto(0)
    }

    // --- EDIT_REGIONS ---------------------------------------------------------------

    private fun openPhoto(index: Int) = viewModelScope.launch {
        val path = _state.value.queue[index]
        _state.update {
            it.copy(step = ScanStep.BUSY, busyMessage = "Cerco gli oggetti nella foto…", index = index,
                bitmap = null, regions = emptyList(), results = emptyList(), selectedRegion = null)
        }
        val bitmap = withContext(Dispatchers.IO) { storage.loadBitmap(path) }
        if (bitmap == null) {
            _state.update { it.copy(message = "Foto non leggibile, la salto") }
            nextPhoto()
            return@launch
        }
        val detection = recognizer.detect(bitmap, _state.value.detectMethod)
        _state.update {
            it.copy(
                step = ScanStep.EDIT_REGIONS,
                bitmap = bitmap,
                regions = detection.rects.ifEmpty { listOf(NormRect.FULL) }.map { r -> RegionUi(nextRegionId++, r) },
                selectedRegion = null,
                detectUsed = detection.methodUsed
            )
        }
    }

    fun setMode(mode: BrickognizeClient.Mode) = _state.update { it.copy(mode = mode) }

    /** Cambia metodo di rilevamento e, se una foto è aperta, ricalcola i riquadri. */
    fun setDetectMethod(method: Recognizer.DetectMethod) {
        _state.update { it.copy(detectMethod = method) }
        val bitmap = _state.value.bitmap ?: return
        if (_state.value.step != ScanStep.EDIT_REGIONS) return
        viewModelScope.launch {
            _state.update { it.copy(step = ScanStep.BUSY, busyMessage = "Cerco gli oggetti nella foto…") }
            val detection = recognizer.detect(bitmap, method)
            _state.update {
                it.copy(
                    step = ScanStep.EDIT_REGIONS,
                    regions = detection.rects.ifEmpty { listOf(NormRect.FULL) }.map { r -> RegionUi(nextRegionId++, r) },
                    selectedRegion = null,
                    detectUsed = detection.methodUsed,
                    message = if (detection.rects.isEmpty()) "Nessun oggetto trovato con questo metodo" else null
                )
            }
        }
    }

    fun selectRegion(id: Int?) = _state.update { it.copy(selectedRegion = id) }

    fun updateRegion(id: Int, rect: NormRect) = _state.update { s ->
        s.copy(regions = s.regions.map { if (it.id == id) it.copy(rect = rect) else it })
    }

    fun addRegion() {
        val r = RegionUi(nextRegionId++, NormRect(0.25f, 0.25f, 0.75f, 0.75f))
        _state.update { it.copy(regions = it.regions + r, selectedRegion = r.id) }
    }

    fun deleteSelectedRegion() = _state.update { s ->
        s.copy(regions = s.regions.filterNot { it.id == s.selectedRegion }, selectedRegion = null)
    }

    fun useWholePhoto() = _state.update {
        it.copy(regions = listOf(RegionUi(nextRegionId++, NormRect.FULL)), selectedRegion = null)
    }

    // --- REVIEW -----------------------------------------------------------------------

    fun recognize() = viewModelScope.launch {
        val s = _state.value
        val bitmap = s.bitmap ?: return@launch
        if (s.regions.isEmpty()) return@launch
        val results = mutableListOf<ResultUi>()
        s.regions.forEachIndexed { i, region ->
            _state.update {
                it.copy(step = ScanStep.BUSY, busyMessage = "Riconosco l'oggetto ${i + 1} di ${s.regions.size}…")
            }
            val cropPath = storage.saveCrop(bitmap, region.rect)
            pendingFiles += cropPath
            val crop = withContext(Dispatchers.Default) { storage.crop(bitmap, region.rect) }
            val rec = recognizer.recognize(crop, s.mode)
            if (crop !== bitmap) crop.recycle()
            results += ResultUi(
                regionId = region.id,
                cropPath = cropPath,
                candidates = rec.candidates,
                selected = if (rec.candidates.isEmpty()) -1 else 0,
                numbersRead = rec.numbersRead,
                raw = rec.brickognizeRaw,
                errors = rec.errors
            )
        }
        val owned = runCatching { repo.owned() }.getOrDefault(emptyMap())
        _state.update { it.copy(step = ScanStep.REVIEW, results = results, owned = owned) }
    }

    fun choose(regionId: Int, index: Int) = _state.update { s ->
        s.copy(results = s.results.map { if (it.regionId == regionId) it.copy(selected = index) else it })
    }

    /** Candidato trovato con la ricerca manuale: va in cima alla lista e viene selezionato. */
    fun setManual(regionId: Int, candidate: Candidate) = _state.update { s ->
        s.copy(results = s.results.map {
            if (it.regionId == regionId) it.copy(candidates = listOf(candidate) + it.candidates, selected = 0) else it
        })
    }

    fun backToRegions() {
        _state.value.results.forEach { discard(it.cropPath) }
        _state.update { it.copy(step = ScanStep.EDIT_REGIONS, results = emptyList()) }
    }

    /** Prima del salvataggio controlla i doppioni: se ce ne sono apre il dialogo. */
    fun saveCurrent() {
        val s = _state.value
        val chosen = s.results.filter { it.selected in it.candidates.indices }
        val seen = mutableSetOf<String>()
        val duplicates = chosen.mapNotNull { r ->
            val c = r.candidates[r.selected]
            val key = c.key()
            val owned = s.owned[key]
            val repeated = !seen.add(key)
            if (owned != null || repeated) {
                DuplicateUi(r.regionId, c.name, c.code, owned?.quantity ?: 0, repeated)
            } else null
        }
        if (duplicates.isEmpty()) performSave(emptySet())
        else _state.update { it.copy(duplicates = duplicates) }
    }

    fun setDuplicateChoice(regionId: Int, add: Boolean) = _state.update { s ->
        s.copy(duplicates = s.duplicates?.map { if (it.regionId == regionId) it.copy(add = add) else it })
    }

    fun confirmDuplicates() {
        val skip = _state.value.duplicates.orEmpty().filterNot { it.add }.map { it.regionId }.toSet()
        _state.update { it.copy(duplicates = null) }
        performSave(skip)
    }

    fun cancelDuplicates() = _state.update { it.copy(duplicates = null) }

    private fun performSave(skipRegions: Set<Int>) = viewModelScope.launch {
        val s = _state.value
        val source = s.queue[s.index]
        val chosen = s.results.filter { it.selected in it.candidates.indices && it.regionId !in skipRegions }
        val saved = mutableListOf<SavedUi>()
        chosen.forEachIndexed { i, r ->
            _state.update { it.copy(step = ScanStep.BUSY, busyMessage = "Salvo ${i + 1} di ${chosen.size} nel catalogo…") }
            val c = r.candidates[r.selected]
            try {
                val res = repo.addFromCandidate(c, r.cropPath, source)
                pendingFiles -= r.cropPath
                pendingFiles -= source
                saved += SavedUi(res.itemId, c.name, res.created, res.warning)
            } catch (e: Exception) {
                saved += SavedUi(-1, c.name, false, "Errore: ${e.message}")
            }
        }
        s.results.filterNot { it in chosen }.forEach { discard(it.cropPath) }
        if (saved.none { it.itemId > 0 }) discard(source)
        _state.update { it.copy(saved = it.saved + saved) }
        nextPhoto()
    }

    fun skipPhoto() {
        val s = _state.value
        s.results.forEach { discard(it.cropPath) }
        discard(s.queue[s.index])
        nextPhoto()
    }

    private fun nextPhoto() {
        val s = _state.value
        if (s.index + 1 < s.queue.size) {
            openPhoto(s.index + 1)
        } else {
            _state.update { it.copy(step = ScanStep.DONE, bitmap = null, regions = emptyList(), results = emptyList()) }
        }
    }

    fun restart() {
        val old = _state.value
        _state.value = ScanState(mode = old.mode, detectMethod = old.detectMethod)
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun discard(path: String) {
        if (path in pendingFiles) {
            storage.deleteFile(path)
            pendingFiles -= path
        }
    }

    override fun onCleared() {
        pendingFiles.forEach { storage.deleteFile(it) }
        pendingFiles.clear()
        storage.clearCaptures()
    }
}
