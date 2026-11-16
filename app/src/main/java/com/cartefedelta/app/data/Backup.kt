package com.cartefedelta.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Esportazione/importazione di tutte le tessere (foto comprese) in un unico file JSON. */
object Backup {
    suspend fun export(context: Context, uri: Uri): Int {
        val cards = AppDatabase.get(context).cards().all()
        val arr = JSONArray()
        cards.forEach { c ->
            arr.put(JSONObject().apply {
                put("name", c.name)
                put("number", c.number)
                put("format", c.format)
                put("color", c.color)
                put("storeKey", c.storeKey ?: JSONObject.NULL)
                put("note", c.note)
                put("displayNumber", c.displayNumber)
                put("favorite", c.favorite)
                put("createdAt", c.createdAt)
                put("lastUsedAt", c.lastUsedAt)
                put("useCount", c.useCount)
                photoB64(context, c.frontPhoto)?.let { put("frontPhoto", it) }
                photoB64(context, c.backPhoto)?.let { put("backPhoto", it) }
            })
        }
        val root = JSONObject().apply {
            put("app", "FreeCard")
            put("version", 1)
            put("cards", arr)
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString().toByteArray()) }
            ?: error("Impossibile scrivere il file")
        return cards.size
    }

    /** Aggiunge le tessere del backup; salta quelle già presenti (stesso nome e numero). */
    suspend fun import(context: Context, uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Impossibile leggere il file")
        val root = JSONObject(text)
        val arr = root.getJSONArray("cards")
        val dao = AppDatabase.get(context).cards()
        val existing = dao.all().map { it.name to it.number }.toSet()
        var added = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.getString("name")
            val number = o.getString("number")
            if ((name to number) in existing) continue
            dao.insert(
                Card(
                    name = name,
                    number = number,
                    format = o.optString("format", "CODE_128"),
                    color = o.optInt("color", 0xFF00897B.toInt()),
                    storeKey = if (o.isNull("storeKey")) null else o.optString("storeKey"),
                    note = o.optString("note", ""),
                    displayNumber = o.optString("displayNumber", ""),
                    favorite = o.optBoolean("favorite", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    lastUsedAt = o.optLong("lastUsedAt", 0),
                    useCount = o.optInt("useCount", 0),
                    frontPhoto = savePhoto(context, o.optString("frontPhoto", "")),
                    backPhoto = savePhoto(context, o.optString("backPhoto", ""))
                )
            )
            added++
        }
        return added
    }

    private fun photoB64(context: Context, name: String?): String? {
        if (name == null) return null
        val f = Photos.file(context, name)
        if (!f.exists()) return null
        return Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }

    private fun savePhoto(context: Context, b64: String): String? {
        if (b64.isEmpty()) return null
        return try {
            val name = "card-${UUID.randomUUID()}.jpg"
            Photos.file(context, name).writeBytes(Base64.decode(b64, Base64.NO_WRAP))
            name
        } catch (e: Exception) {
            null
        }
    }
}
