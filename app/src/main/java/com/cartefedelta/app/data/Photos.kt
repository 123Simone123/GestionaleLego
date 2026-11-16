package com.cartefedelta.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

/** Foto delle tessere salvate nella memoria privata dell'app. */
object Photos {
    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** File vuoto + Uri da passare all'app Fotocamera. */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val f = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        return f to uri
    }

    /** Copia (ridimensionando) un'immagine nella cartella privata e restituisce il nome del file. */
    fun importImage(context: Context, uri: Uri): String? = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        saveScaled(context, bytes)
    } catch (e: Exception) {
        null
    }

    fun importFile(context: Context, file: File): String? = try {
        saveScaled(context, file.readBytes()).also { file.delete() }
    } catch (e: Exception) {
        null
    }

    private fun saveScaled(context: Context, bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val rotation = try {
            ExifInterface(bytes.inputStream()).rotationDegrees
        } catch (e: Exception) {
            0
        }
        if (rotation != 0) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        }
        val maxSide = 1600
        if (maxOf(bmp.width, bmp.height) > maxSide) {
            val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        }
        val name = "card-${UUID.randomUUID()}.jpg"
        file(context, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return name
    }

    fun delete(context: Context, name: String?) {
        if (name != null) file(context, name).delete()
    }
}
