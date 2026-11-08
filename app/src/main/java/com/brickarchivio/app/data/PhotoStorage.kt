package com.brickarchivio.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tutti i file dell'app stanno nella memoria interna privata (context.filesDir):
 *
 *  files/photos/originals/  foto intere scattate o importate
 *  files/photos/crops/      ritagli dei singoli oggetti (collegati agli elementi)
 *  files/official/          immagini ufficiali scaricate da Rebrickable/Brickognize
 *
 * Nulla viene scritto nella galleria del telefono.
 */
class PhotoStorage(private val context: Context) {

    val originalsDir get() = File(context.filesDir, "photos/originals").apply { mkdirs() }
    val cropsDir get() = File(context.filesDir, "photos/crops").apply { mkdirs() }
    val officialDir get() = File(context.filesDir, "official").apply { mkdirs() }
    private val capturesDir get() = File(context.cacheDir, "captures").apply { mkdirs() }

    /** Uri temporaneo dove la fotocamera di sistema scrive lo scatto. */
    fun newCaptureUri(): Uri {
        val file = File(capturesDir, "cap_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun clearCaptures() {
        capturesDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Copia un'immagine (fotocamera o galleria) nella memoria dell'app, raddrizzata
     * secondo l'EXIF e ridotta a [maxSide] px sul lato lungo. Restituisce il percorso.
     */
    suspend fun importImage(uri: Uri, maxSide: Int = 2048): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeNormalized(uri, maxSide) ?: return@withContext null
        val file = File(originalsDir, "orig_${UUID.randomUUID()}.jpg")
        saveJpeg(bitmap, file, 90)
        bitmap.recycle()
        file.absolutePath
    }

    fun loadBitmap(path: String): Bitmap? = BitmapFactory.decodeFile(path)

    /** Ritaglia [bitmap] nel rettangolo normalizzato (0..1) e salva il ritaglio. */
    suspend fun saveCrop(bitmap: Bitmap, rect: NormRect): String = withContext(Dispatchers.IO) {
        val crop = crop(bitmap, rect)
        val file = File(cropsDir, "crop_${UUID.randomUUID()}.jpg")
        saveJpeg(crop, file, 90)
        if (crop !== bitmap) crop.recycle()
        file.absolutePath
    }

    fun crop(bitmap: Bitmap, rect: NormRect): Bitmap {
        val r = rect.normalized()
        val left = (r.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (r.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (r.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (r.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /** JPEG ridotto da inviare a Brickognize. */
    fun toUploadJpeg(bitmap: Bitmap, maxSide: Int = 1024): ByteArray {
        val scaled = scaleDown(bitmap, maxSide)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (scaled !== bitmap) scaled.recycle()
        return out.toByteArray()
    }

    fun officialFileFor(type: String, code: String): File {
        val safe = code.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(officialDir, "${type.lowercase()}_$safe.jpg")
    }

    fun deleteFile(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    // ---------------------------------------------------------------------------------

    private fun decodeNormalized(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // con inJustDecodeBounds decodeStream restituisce sempre null: contano solo le dimensioni lette
        val stream = resolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val rotated = applyOrientation(decoded, orientation)
        return scaleDown(rotated, maxSide)
    }

    private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longSide = max(src.width, src.height)
        if (longSide <= maxSide) return src
        val f = maxSide.toFloat() / longSide
        return Bitmap.createScaledBitmap(src, (src.width * f).roundToInt(), (src.height * f).roundToInt(), true)
    }

    private fun saveJpeg(bitmap: Bitmap, file: File, quality: Int) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }
}

/** Rettangolo in coordinate normalizzate 0..1 rispetto all'immagine. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    fun normalized(): NormRect = NormRect(
        minOf(left, right).coerceIn(0f, 1f),
        minOf(top, bottom).coerceIn(0f, 1f),
        maxOf(left, right).coerceIn(0f, 1f),
        maxOf(top, bottom).coerceIn(0f, 1f)
    )

    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom

    companion object {
        val FULL = NormRect(0f, 0f, 1f, 1f)
    }
}
