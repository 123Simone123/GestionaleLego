package com.brickarchivio.app.data.remote

import com.brickarchivio.app.domain.ItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Client per l'API v3 di Rebrickable (https://rebrickable.com/api/v3/docs/).
 *
 * - Autenticazione: header "Authorization: key <CHIAVE>" (verificato dalla documentazione).
 * - Limite: in media 1 richiesta al secondo; oltre si riceve HTTP 429 e, insistendo,
 *   l'IP può essere bloccato per 1-2 giorni. Per questo tutte le chiamate passano da
 *   [throttled], che le distanzia di almeno [MIN_INTERVAL_MS].
 */
class RebrickableClient(private val apiKeyProvider: () -> String?) {

    private val mutex = Mutex()
    private var lastCall = 0L
    private val themeCache = mutableMapOf<Int, String>()
    private val partCategoryCache = mutableMapOf<Int, String>()

    fun hasKey(): Boolean = !apiKeyProvider().isNullOrBlank()

    /** GET /lego/sets/{set_num}/ — null se il set non esiste. */
    suspend fun getSet(setNum: String): Candidate? {
        val json = get(url("lego/sets/${enc(setNum)}/")) ?: return null
        return setFromJson(json, withTheme = true)
    }

    /** GET /lego/sets/?search=... (cerca su nome e numero). */
    suspend fun searchSets(query: String): List<Candidate> {
        val json = get(
            url("lego/sets/").newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("page_size", "20")
                .build()
        ) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            results.optJSONObject(i)?.let { setFromJson(it, withTheme = false) }
        }
    }

    /** GET /lego/minifigs/{fig_num}/ (codici Rebrickable, es. fig-000001). */
    suspend fun getMinifig(figNum: String): Candidate? {
        val json = get(url("lego/minifigs/${enc(figNum)}/")) ?: return null
        return minifigFromJson(json)
    }

    /** GET /lego/minifigs/?search=... */
    suspend fun searchMinifigs(query: String): List<Candidate> {
        val json = get(
            url("lego/minifigs/").newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("page_size", "20")
                .build()
        ) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            results.optJSONObject(i)?.let { minifigFromJson(it) }
        }
    }

    /** GET /lego/parts/{part_num}/ */
    suspend fun getPart(partNum: String): Candidate? {
        val json = get(url("lego/parts/${enc(partNum)}/")) ?: return null
        val catId = json.optInt("part_cat_id", -1)
        return Candidate(
            type = ItemType.PART,
            code = json.optString("part_num").ifBlank { partNum },
            name = json.optString("name").ifBlank { partNum },
            imageUrl = json.optString("part_img_url").ifBlank { null }.takeUnless { it == "null" },
            category = if (catId >= 0) partCategoryName(catId) else null,
            rebrickableUrl = json.optString("part_url").ifBlank { null },
            source = "Rebrickable"
        )
    }

    /** GET /lego/parts/?search=... */
    suspend fun searchParts(query: String): List<Candidate> {
        val json = get(
            url("lego/parts/").newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("page_size", "20")
                .build()
        ) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val p = results.optJSONObject(i) ?: return@mapNotNull null
            val num = p.optString("part_num")
            if (num.isBlank()) null else Candidate(
                type = ItemType.PART,
                code = num,
                name = p.optString("name").ifBlank { num },
                imageUrl = p.optString("part_img_url").ifBlank { null }.takeUnless { it == "null" },
                rebrickableUrl = p.optString("part_url").ifBlank { null },
                source = "Rebrickable"
            )
        }
    }

    private suspend fun setFromJson(json: JSONObject, withTheme: Boolean): Candidate? {
        val num = json.optString("set_num")
        if (num.isBlank()) return null
        val themeId = json.optInt("theme_id", -1)
        return Candidate(
            type = ItemType.SET,
            code = num,
            name = json.optString("name").ifBlank { num },
            imageUrl = json.optString("set_img_url").ifBlank { null }.takeUnless { it == "null" },
            year = json.optInt("year", 0).takeIf { it > 0 },
            theme = if (withTheme && themeId >= 0) themeName(themeId) else null,
            numParts = json.optInt("num_parts", -1).takeIf { it >= 0 },
            rebrickableUrl = json.optString("set_url").ifBlank { null },
            source = "Rebrickable"
        )
    }

    private fun minifigFromJson(json: JSONObject): Candidate? {
        val num = json.optString("set_num")
        if (num.isBlank()) return null
        return Candidate(
            type = ItemType.MINIFIG,
            code = num,
            name = json.optString("name").ifBlank { num },
            imageUrl = json.optString("set_img_url").ifBlank { null }.takeUnless { it == "null" },
            numParts = json.optInt("num_parts", -1).takeIf { it >= 0 },
            rebrickableUrl = json.optString("set_url").ifBlank { null },
            source = "Rebrickable"
        )
    }

    /** Nome del tema (con il tema padre, es. "Star Wars › Ultimate Collector Series"). */
    suspend fun themeName(id: Int): String? {
        themeCache[id]?.let { return it }
        val json = get(url("lego/themes/$id/")) ?: return null
        val name = json.optString("name").ifBlank { return null }
        val parentId = json.optInt("parent_id", -1)
        val full = if (parentId > 0) {
            val parent = themeName(parentId)
            if (parent != null) "$parent › $name" else name
        } else name
        themeCache[id] = full
        return full
    }

    private suspend fun partCategoryName(id: Int): String? {
        partCategoryCache[id]?.let { return it }
        val json = get(url("lego/part_categories/$id/")) ?: return null
        val name = json.optString("name").ifBlank { return null }
        partCategoryCache[id] = name
        return name
    }

    /** Verifica la chiave con una chiamata leggera. */
    suspend fun testKey(): Boolean = runCatching { get(url("lego/colors/").newBuilder()
        .addQueryParameter("page_size", "1").build()) != null }.getOrDefault(false)

    // ---------------------------------------------------------------------------------

    private fun url(path: String): HttpUrl = "$BASE_URL/$path".toHttpUrl()
    private fun enc(s: String) = java.net.URLEncoder.encode(s.trim(), "UTF-8")

    /** GET con limite di frequenza. Restituisce null su 404. */
    private suspend fun get(url: HttpUrl): JSONObject? {
        val key = apiKeyProvider()?.trim()
        if (key.isNullOrEmpty()) throw ApiException("Chiave API Rebrickable mancante (Impostazioni)")
        repeat(2) { attempt ->
            val (code, text) = throttled {
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "key $key")
                    .header("Accept", "application/json")
                    .header("User-Agent", Http.USER_AGENT)
                    .build()
                withContext(Dispatchers.IO) {
                    try {
                        Http.client.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
                    } catch (e: IOException) {
                        throw ApiException("Rebrickable non raggiungibile: ${e.message}")
                    }
                }
            }
            when {
                code in 200..299 -> return runCatching { JSONObject(text) }.getOrNull()
                code == 404 -> return null
                code == 401 || code == 403 -> throw ApiException("Chiave API Rebrickable non valida", code)
                code == 429 && attempt == 0 -> delay(3_000) // troppo veloce: aspetta e riprova una volta
                else -> throw ApiException("Rebrickable ha risposto $code", code)
            }
        }
        throw ApiException("Rebrickable: troppe richieste, riprova tra poco", 429)
    }

    private suspend fun <T> throttled(block: suspend () -> T): T = mutex.withLock {
        val wait = lastCall + MIN_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            lastCall = System.currentTimeMillis()
        }
    }

    companion object {
        const val BASE_URL = "https://rebrickable.com/api/v3"
        const val MIN_INTERVAL_MS = 1_100L
    }
}
