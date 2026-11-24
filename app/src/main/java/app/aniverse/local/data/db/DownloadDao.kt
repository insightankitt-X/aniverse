package app.aniverse.local.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun all(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE episodeId = :episodeId")
    suspend fun byEpisode(episodeId: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE state = :state")
    suspend fun byState(state: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE state IN ('QUEUED','DOWNLOADING')")
    suspend fun active(): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE state IN ('QUEUED','DOWNLOADING')")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM downloads WHERE state = 'COMPLETED'")
    suspend fun completed(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity): Long

    @Query(
        "UPDATE downloads SET state = :state, progress = :progress, bytesDownloaded = :bytes, " +
            "totalBytes = :total, errorMessage = :error, updatedAt = :at WHERE episodeId = :episodeId",
    )
    suspend fun updateState(
        episodeId: Long,
        state: String,
        progress: Float,
        bytes: Long,
        total: Long,
        error: String?,
        at: Long,
    )

    @Query(
        "UPDATE downloads SET filePath = :path, state = 'COMPLETED', progress = 1.0, " +
            "errorMessage = NULL, updatedAt = :at WHERE episodeId = :episodeId",
    )
    suspend fun markCompleted(episodeId: Long, path: String, at: Long)

    @Query("UPDATE downloads SET quality = :quality WHERE episodeId = :episodeId")
    suspend fun setQuality(episodeId: Long, quality: String?)

    @Query("DELETE FROM downloads WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: Long)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloads WHERE state = 'COMPLETED'")
    suspend fun completedBytes(): Long

    @Query("SELECT * FROM downloads WHERE episodeId = :episodeId")
    fun observeByEpisode(episodeId: Long): Flow<DownloadEntity?>
}
