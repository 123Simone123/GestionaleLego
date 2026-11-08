package com.brickarchivio.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Versioni dello schema (i file JSON sono in app/schemas/, necessari alle migrazioni):
 *  1 - catalogo: items, photos, tags, item_tags
 *  2 - inventario: containers, container_entries, exhibitions,
 *      exhibition_containers, exhibition_checks (migrazione automatica, dati conservati)
 */
@Database(
    entities = [
        ItemEntity::class, PhotoEntity::class, TagEntity::class, ItemTagCrossRef::class,
        ContainerEntity::class, ContainerEntryEntity::class, ExhibitionEntity::class,
        ExhibitionContainerEntity::class, ExhibitionCheckEntity::class
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun inventoryDao(): InventoryDao

    companion object {
        const val NAME = "brickarchivio.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
