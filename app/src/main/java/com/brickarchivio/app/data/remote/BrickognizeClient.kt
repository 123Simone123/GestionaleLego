package com.brickarchivio.app.data.remote

import com.brickarchivio.app.domain.ItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Client per Brickognize (https://brickognize.com), servizio gratuito senza chiave
 * che riconosce set, minifigure e pezzi da una foto.
 *
 * Richiesta (verificata): POST https://api.brickognize.com/predict/
 * multipart con campo "query_image" contenente il JPEG.
 *
 * Risposta: il parsing è volutamente tollerante (campi letti con optString) perché
 * lo schema completo non è stato verificabile durante la progettazione: vedi
 * docs/DOCUMENTAZIONE.md (sezione 5). La risposta grezza viene conservata in [Result.raw] e
 * mostrata nella schermata di revisione ("Dettagli tecnici") per poterla controllare.
 */
class BrickognizeClient {

    data class Result(val candidates: List<Candidate>, val raw: String)

    suspend fun predict(jpeg: ByteArray): Result = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "query_image", "photo.jpg",
                jpeg.toRequestBody("image/jpeg".toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url("$BASE_URL/predict/")
            .header("Accept", "application/json")
            .header("User-Agent", Http.USER_AGENT)
            .post(body)
            .build()
        try {
            Http.client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw ApiException("Brickognize ha risposto ${resp.code}", resp.code)
                }
                Result(parse(text), text)
            }
        } catch (e: IOException) {
            throw ApiException("Brickognize non raggiungibile: ${e.message}")
        }
    }

    internal fun parse(text: String): List<Candidate> {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val items = root.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<Candidate>()
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            val id = it.optString("id").trim()
            if (id.isEmpty()) continue
            val rawType = it.optString("type").lowercase()
            val type = when {
                rawType.contains("set") -> ItemType.SET
                rawType.contains("fig") -> ItemType.MINIFIG
                else -> ItemType.PART
            }
            val sites = it.optJSONArray("external_sites")
            out += Candidate(
                type = type,
                code = if (type == ItemType.SET) normalizeSetNum(id) else id,
                name = it.optString("name").ifBlank { id },
                imageUrl = it.optString("img_url").ifBlank { null },
                category = it.optString("category").ifBlank { null },
                score = if (it.has("score")) it.optDouble("score") else null,
                bricklinkUrl = findSite(sites, "bricklink"),
                rebrickableUrl = findSite(sites, "rebrickable"),
                source = "Brickognize"
            )
        }
        return out
    }

    private fun findSite(sites: JSONArray?, name: String): String? {
        if (sites == null) return null
        for (i in 0 until sites.length()) {
            val s = sites.optJSONObject(i) ?: continue
            if (s.optString("name").contains(name, ignoreCase = true)) {
                return s.optString("url").ifBlank { null }
            }
        }
        return null
    }

    companion object {
        const val BASE_URL = "https://api.brickognize.com"

        /** "75192" -> "75192-1"; "75192-1" resta uguale. Formato usato da Rebrickable. */
        fun normalizeSetNum(id: String): String = if (id.contains('-')) id else "$id-1"
    }
}
