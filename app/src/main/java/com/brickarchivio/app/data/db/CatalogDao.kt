package com.brickarchivio.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Transaction
    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ItemWithDetails>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun observeItem(id: Long): Flow<ItemWithDetails?>

    @Query("SELECT * FROM items WHERE type = :type AND code = :code LIMIT 1")
    suspend fun findByCode(type: String, code: String): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItem(id: Long): ItemEntity?

    @Insert
    suspend fun insertItem(item: ItemEntity): Long

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Insert
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Query("SELECT * FROM photos WHERE itemId = :itemId")
    suspend fun photosOf(itemId: Long): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhoto(id: Long): PhotoEntity?

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    @Query("SELECT COUNT(*) FROM photos WHERE sourcePath = :sourcePath")
    suspend fun countPhotosWithSource(sourcePath: String): Int

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTag(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToItem(ref: ItemTagCrossRef)

    @Query("DELETE FROM item_tags WHERE itemId = :itemId AND tagId = :tagId")
    suspend fun removeTagFromItem(itemId: Long, tagId: Long)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM item_tags)")
    suspend fun deleteUnusedTags()
}
