package br.com.jcdecor.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingLocationDao {
    @Insert
    suspend fun insert(entity: PendingLocationEntity): Long

    @Query("SELECT * FROM pending_location ORDER BY created_at ASC, sequence ASC, id ASC")
    suspend fun listInSendOrder(): List<PendingLocationEntity>

    @Query("DELETE FROM pending_location WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_location SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("SELECT COUNT(*) FROM pending_location")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_location")
    fun observeCount(): Flow<Int>
}
