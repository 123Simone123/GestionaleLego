package com.brickarchivio.app.data

import android.content.Context
import android.net.Uri
import com.brickarchivio.app.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup completo in un unico file .zip (database + foto + immagini ufficiali).
 * I dati sono solo sul telefono: il backup è l'unico modo per non perderli se
 * disinstalli l'app o cambi telefono.
 *
 * Struttura dello zip:
 *   db/brickarchivio.db (+ -wal, -shm se presenti)
 *   files/photos/...
 *   files/official/...
 */
object Backup {

    suspend fun export(context: Context, db: AppDatabase, target: Uri) = withContext(Dispatchers.IO) {
        // scrive nel file principale le modifiche ancora nel log WAL
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        val out = context.contentResolver.openOutputStream(target) ?: error("Impossibile scrivere il file")
        ZipOutputStream(out.buffered()).use { zip ->
            listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"))
                .filter { it.exists() }
                .forEach { addFile(zip, it, "db/${it.name}") }
            for (dirName in listOf("photos", "official")) {
                val dir = File(context.filesDir, dirName)
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    addFile(zip, f, "files/" + f.relativeTo(context.filesDir).path)
                }
            }
        }
    }

    /**
     * Sostituisce i dati attuali con quelli del backup. Dopo l'importazione l'app
     * va riavviata (lo fa il chiamante).
     */
    suspend fun import(context: Context, db: AppDatabase, source: Uri) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(source) ?: error("Impossibile leggere il file")

        // verifica preliminare: deve contenere il database
        val valid = ZipInputStream(context.contentResolver.openInputStream(source)!!.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.any { it.name == "db/${AppDatabase.NAME}" }
        }
        if (!valid) {
            input.close()
            error("Il file non è un backup di Brick Archivio")
        }

        db.close()
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach { it.delete() }
        File(context.filesDir, "photos").deleteRecursively()
        File(context.filesDir, "official").deleteRecursively()

        val filesRoot = context.filesDir.canonicalFile
        val dbRoot = dbFile.parentFile!!.canonicalFile
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val dest = when {
                    entry.name.startsWith("db/") -> File(dbRoot, entry.name.removePrefix("db/"))
                    entry.name.startsWith("files/") -> File(filesRoot, entry.name.removePrefix("files/"))
                    else -> null
                }?.canonicalFile ?: continue
                // ignora percorsi che uscirebbero dalle cartelle dell'app
                if (!dest.path.startsWith(filesRoot.path) && !dest.path.startsWith(dbRoot.path)) continue
                dest.parentFile?.mkdirs()
                dest.outputStream().use { zip.copyTo(it) }
            }
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
