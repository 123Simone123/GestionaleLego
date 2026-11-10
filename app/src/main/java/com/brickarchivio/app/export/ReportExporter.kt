package com.brickarchivio.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.brickarchivio.app.domain.Exhibition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", "application/pdf"),
    DOCX("Word", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
}

/** Crea i file di esportazione e li condivide o salva. */
object ReportExporter {

    suspend fun write(reports: List<InventoryReport>, format: ExportFormat, out: OutputStream) =
        withContext(Dispatchers.IO) {
            when (format) {
                ExportFormat.PDF -> PdfWriter.write(reports, out)
                ExportFormat.DOCX -> DocxWriter.write(reports, out)
            }
        }

    /** Nome file, es. "Inventario 2026-10-04 Mostra Lucca.pdf" */
    fun fileName(ex: Exhibition, format: ExportFormat): String {
        val parts = listOfNotNull("Inventario", ex.startDate, ex.name)
            .joinToString(" ")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(80)
        return "$parts.${format.extension}"
    }

    /** Scrive il file nella cache e restituisce l'Intent "Condividi" (WhatsApp, email, Drive, stampa…). */
    suspend fun shareIntent(context: Context, reports: List<InventoryReport>, format: ExportFormat, fileName: String): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // tiene solo l'ultimo export
        val file = File(dir, fileName)
        file.outputStream().use { write(reports, format, it) }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName.substringBeforeLast('.'))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Condividi inventario")
    }
}
