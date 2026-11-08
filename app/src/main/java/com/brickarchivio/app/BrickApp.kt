package com.brickarchivio.app

import android.app.Application
import com.brickarchivio.app.data.CatalogRepository
import com.brickarchivio.app.data.InventoryRepository
import com.brickarchivio.app.data.PhotoStorage
import com.brickarchivio.app.data.Settings
import com.brickarchivio.app.data.db.AppDatabase
import com.brickarchivio.app.data.remote.BrickognizeClient
import com.brickarchivio.app.data.remote.RebrickableClient
import com.brickarchivio.app.recognition.Recognizer

/** Contenitore delle dipendenze (iniezione manuale, senza librerie). */
class BrickApp : Application() {

    lateinit var settings: Settings
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var storage: PhotoStorage
        private set
    lateinit var rebrickable: RebrickableClient
        private set
    lateinit var repository: CatalogRepository
        private set
    lateinit var recognizer: Recognizer
        private set
    lateinit var inventory: InventoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        database = AppDatabase.build(this)
        storage = PhotoStorage(this)
        rebrickable = RebrickableClient { settings.rebrickableKey }
        repository = CatalogRepository(database.catalogDao(), storage, rebrickable)
        recognizer = Recognizer(storage, BrickognizeClient(), rebrickable)
        inventory = InventoryRepository(database.inventoryDao())
    }
}
