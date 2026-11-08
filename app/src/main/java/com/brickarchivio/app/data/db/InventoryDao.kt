package com.brickarchivio.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    // --- Contenitori --------------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM containers ORDER BY name COLLATE NOCASE")
    fun observeContainers(): Flow<List<ContainerWithEntries>>

    @Transaction
    @Query("SELECT * FROM containers WHERE id = :id")
    fun observeContainer(id: Long): Flow<ContainerWithEntries?>

    @Query("SELECT * FROM containers WHERE id = :id")
    suspend fun getContainer(id: Long): ContainerEntity?

    @Insert
    suspend fun insertContainer(container: ContainerEntity): Long

    @Update
    suspend fun updateContainer(container: ContainerEntity)

    @Query("DELETE FROM containers WHERE id = :id")
    suspend fun deleteContainer(id: Long)

    // --- Righe dei contenitori -----------------------------------------------------

    @Insert
    suspend fun insertEntry(entry: ContainerEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: ContainerEntryEntity)

    @Query("SELECT * FROM container_entries WHERE id = :id")
    suspend fun getEntry(id: Long): ContainerEntryEntity?

    @Query("DELETE FROM container_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM container_entries WHERE containerId = :containerId AND itemId = :itemId LIMIT 1")
    suspend fun findEntryForItem(containerId: Long, itemId: Long): ContainerEntryEntity?

    @Query("SELECT COALESCE(MAX(position), 0) FROM container_entries WHERE containerId = :containerId")
    suspend fun maxPosition(containerId: Long): Int

    @Query("SELECT id FROM container_entries WHERE containerId = :containerId")
    suspend fun entryIdsOf(containerId: Long): List<Long>

    /** In quali contenitori si trova un oggetto del catalogo. */
    @Query(
        """
        SELECT c.id AS containerId, c.name AS containerName, SUM(e.quantity) AS quantity
        FROM container_entries e JOIN containers c ON c.id = e.containerId
        WHERE e.itemId = :itemId
        GROUP BY c.id, c.name
        ORDER BY c.name COLLATE NOCASE
        """
    )
    fun observeLocations(itemId: Long): Flow<List<ItemLocation>>

    // --- Esposizioni ----------------------------------------------------------------

    @Query(
        """
        SELECT x.*,
          (SELECT COUNT(*) FROM exhibition_containers ec WHERE ec.exhibitionId = x.id) AS containerCount,
          (SELECT COUNT(*) FROM container_entries e
             JOIN exhibition_containers ec ON ec.containerId = e.containerId
             WHERE ec.exhibitionId = x.id) AS entryCount,
          (SELECT COUNT(*) FROM exhibition_checks k
             JOIN container_entries e ON e.id = k.entryId
             JOIN exhibition_containers ec ON ec.containerId = e.containerId AND ec.exhibitionId = k.exhibitionId
             WHERE k.exhibitionId = x.id AND k.outbound = 1) AS outboundCount,
          (SELECT COUNT(*) FROM exhibition_checks k
             JOIN container_entries e ON e.id = k.entryId
             JOIN exhibition_containers ec ON ec.containerId = e.containerId AND ec.exhibitionId = k.exhibitionId
             WHERE k.exhibitionId = x.id AND k.returned = 1) AS returnedCount,
          (SELECT COUNT(*) FROM exhibition_checks k
             JOIN container_entries e ON e.id = k.entryId
             JOIN exhibition_containers ec ON ec.containerId = e.containerId AND ec.exhibitionId = k.exhibitionId
             WHERE k.exhibitionId = x.id AND k.outbound = 1 AND k.returned = 0) AS missingCount
        FROM exhibitions x
        ORDER BY COALESCE(x.startDate, '') DESC, x.createdAt DESC
        """
    )
    fun observeExhibitionSummaries(): Flow<List<ExhibitionSummary>>

    @Query("SELECT * FROM exhibitions WHERE id = :id")
    fun observeExhibition(id: Long): Flow<ExhibitionEntity?>

    @Query("SELECT * FROM exhibitions WHERE id = :id")
    suspend fun getExhibition(id: Long): ExhibitionEntity?

    @Insert
    suspend fun insertExhibition(exhibition: ExhibitionEntity): Long

    @Update
    suspend fun updateExhibition(exhibition: ExhibitionEntity)

    @Query("DELETE FROM exhibitions WHERE id = :id")
    suspend fun deleteExhibition(id: Long)

    @Query("SELECT containerId FROM exhibition_containers WHERE exhibitionId = :exhibitionId")
    fun observeExhibitionContainerIds(exhibitionId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addExhibitionContainer(link: ExhibitionContainerEntity)

    @Query("DELETE FROM exhibition_containers WHERE exhibitionId = :exhibitionId AND containerId = :containerId")
    suspend fun removeExhibitionContainer(exhibitionId: Long, containerId: Long)

    @Query("SELECT * FROM exhibition_checks WHERE exhibitionId = :exhibitionId")
    fun observeChecks(exhibitionId: Long): Flow<List<ExhibitionCheckEntity>>

    @Query("SELECT * FROM exhibition_checks WHERE exhibitionId = :exhibitionId AND entryId = :entryId")
    suspend fun getCheck(exhibitionId: Long, entryId: Long): ExhibitionCheckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheck(check: ExhibitionCheckEntity)
}
