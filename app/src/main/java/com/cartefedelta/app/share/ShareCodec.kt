package com.cartefedelta.app.share

import java.util.Base64
import com.cartefedelta.app.data.Card
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Formato dei QR di condivisione, uguale nell'app Android e nella webapp:
 *
 *   FREECARD1|z|<id>|<parte>|<totale>|<dati>
 *
 * <dati> sono un pezzo del base64 di un JSON compresso (deflate "raw", flag z)
 * oppure non compresso (flag j). Il JSON è {"v":1,"c":[{n,k,f,col,s,o,d,p}, ...]}.
 * Le foto non viaggiano nel QR (sarebbero troppo grandi): per quelle c'è il backup.
 */
object ShareCodec {
    const val PREFIX = "FREECARD1|"
    private const val CHUNK = 400

    data class SharedCard(
        val name: String,
        val number: String,
        val format: String,
        val color: Int,
        val storeKey: String?,
        val note: String,
        val displayNumber: String,
        val favorite: Boolean
    )

    fun fromCard(c: Card) = SharedCard(c.name, c.number, c.format, c.color, c.storeKey, c.note, c.displayNumber, c.favorite)

    fun toJson(cards: List<SharedCard>): String {
        val arr = JSONArray()
        cards.forEach { c ->
            arr.put(JSONObject().apply {
                put("n", c.name)
                put("k", c.number)
                put("f", c.format)
                put("col", c.color)
                if (c.storeKey != null) put("s", c.storeKey)
                if (c.note.isNotEmpty()) put("o", c.note)
                if (c.displayNumber.isNotEmpty()) put("d", c.displayNumber)
                if (c.favorite) put("p", 1)
            })
        }
        return JSONObject().put("v", 1).put("c", arr).toString()
    }

    fun parseJson(json: String): List<SharedCard> {
        val arr = JSONObject(json).getJSONArray("c")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SharedCard(
                name = o.getString("n"),
                number = o.getString("k"),
                format = o.optString("f", "CODE_128"),
                color = o.optInt("col", 0xFF00897B.toInt()),
                storeKey = if (o.has("s") && !o.isNull("s")) o.getString("s") else null,
                note = o.optString("o", ""),
                displayNumber = o.optString("d", ""),
                favorite = o.optInt("p", 0) == 1
            )
        }
    }

    /** Testi da mettere nei QR (uno o più). */
    fun encode(cards: List<SharedCard>): List<String> {
        val data = Base64.getEncoder().encodeToString(deflate(toJson(cards).toByteArray(Charsets.UTF_8)))
        val id = (1..4).map { "abcdefghijkmnpqrstuvwxyz23456789".random() }.joinToString("")
        val parts = data.chunked(CHUNK).ifEmpty { listOf("") }
        return parts.mapIndexed { i, p -> "${PREFIX}z|$id|${i + 1}|${parts.size}|$p" }
    }

    fun deflate(bytes: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        d.setInput(bytes)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    fun inflate(bytes: ByteArray): ByteArray {
        val inf = Inflater(true)
        // L'Inflater "raw" vuole un byte in più alla fine
        inf.setInput(bytes + byteArrayOf(0))
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }

    /** Raccoglie le parti lette dalla fotocamera finché non sono complete. */
    class Collector {
        private var id: String? = null
        private var flag = "z"
        private var total = 0
        private val parts = HashMap<Int, String>()

        val received get() = parts.size
        val expected get() = total

        /** true se il testo era un QR FreeCard valido (nuovo o già letto). */
        fun add(text: String): Boolean {
            if (!text.startsWith(PREFIX)) return false
            val f = text.split("|", limit = 6)
            if (f.size != 6) return false
            val (flg, pid) = f[1] to f[2]
            val idx = f[3].toIntOrNull() ?: return false
            val tot = f[4].toIntOrNull() ?: return false
            if (tot < 1 || idx < 1 || idx > tot) return false
            if (pid != id) {
                id = pid; flag = flg; total = tot; parts.clear()
            }
            parts[idx] = f[5]
            return true
        }

        fun isComplete() = total > 0 && parts.size == total

        fun result(): List<SharedCard> {
            val data = (1..total).joinToString("") { parts[it] ?: "" }
            val bytes = Base64.getDecoder().decode(data)
            val json = if (flag == "z") inflate(bytes) else bytes
            return parseJson(json.toString(Charsets.UTF_8))
        }
    }
}
