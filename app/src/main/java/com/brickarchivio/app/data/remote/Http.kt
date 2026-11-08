package com.brickarchivio.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Scarica un file (es. immagine ufficiale) nella destinazione indicata. */
    suspend fun download(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body ?: return@withContext false
                dest.parentFile?.mkdirs()
                val tmp = File(dest.parentFile, dest.name + ".tmp")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest)
            }
        } catch (e: IOException) {
            false
        }
    }

    const val USER_AGENT = "BrickArchivio/1.0 (Android; uso personale)"
}

class ApiException(message: String, val httpCode: Int? = null) : Exception(message)
