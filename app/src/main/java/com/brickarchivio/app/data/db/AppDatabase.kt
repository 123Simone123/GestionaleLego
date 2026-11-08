package com.brickarchivio.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ItemEntity::class, PhotoEntity::class, TagEntity::class, ItemTagCrossRef::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        const val NAME = "brickarchivio.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
