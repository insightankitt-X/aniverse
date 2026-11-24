package app.anilab.local.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Projection used to draw category counts without loading every series row. */
data class CategoryCount(val categoryId: Long, val count: Int)

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE inLibrary = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeLibrary(): Flow<List<SeriesEntity>>

    @Query(
        "SELECT s.* FROM series s INNER JOIN series_categories c ON c.seriesId = s.id " +
            "WHERE c.categoryId = :categoryId AND s.inLibrary = 1 ORDER BY s.title COLLATE NOCASE ASC",
    )
    fun observeInCategory(categoryId: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE inLibrary = 1 ORDER BY addedToLibraryAt DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :id")
    fun observeById(id: Long): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun byId(id: Long): SeriesEntity?

    @Query(
        "SELECT * FROM series WHERE sourcePkg = :pkg AND sourceId = :sourceId AND remoteId = :remoteId LIMIT 1",
    )
    suspend fun find(pkg: String, sourceId: Long, remoteId: String): SeriesEntity?

    @Query(
        "SELECT * FROM series WHERE sourcePkg = :pkg AND sourceId = :sourceId AND remoteId = :remoteId LIMIT 1",
    )
    fun observeByRemote(pkg: String, sourceId: Long, remoteId: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE inLibrary = 1")
    suspend fun librarySeries(): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE inLibrary = 1 AND sourcePkg = :pkg AND sourceId = :sourceId")
    suspend fun libraryForSource(pkg: String, sourceId: Long): List<SeriesEntity>

    @Query("SELECT * FROM series")
    suspend fun all(): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<SeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(series: SeriesEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(series: List<SeriesEntity>)

    @Query("UPDATE series SET inLibrary = 1, addedToLibraryAt = :at WHERE id = :id")
    suspend fun addToLibrary(id: Long, at: Long)

    @Query("UPDATE series SET inLibrary = 0, addedToLibraryAt = 0 WHERE id = :id")
    suspend fun removeFromLibrary(id: Long)

    @Query("UPDATE series SET inLibrary = 0, addedToLibraryAt = 0 WHERE id IN (:ids)")
    suspend fun removeManyFromLibrary(ids: List<Long>)

    @Query("UPDATE series SET lastUpdatedAt = :at, lastFetchedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("SELECT COUNT(*) FROM series WHERE inLibrary = 1")
    fun observeLibraryCount(): Flow<Int>

    @Query("SELECT * FROM series WHERE inLibrary = 1 AND title LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC")
    fun searchLibrary(query: String): Flow<List<SeriesEntity>>
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY number ASC, name COLLATE NOCASE ASC")
    fun observeForSeries(seriesId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY number ASC, name COLLATE NOCASE ASC")
    suspend fun forSeries(seriesId: Long): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun byId(id: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE id = :id")
    fun observeById(id: Long): Flow<EpisodeEntity?>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND remoteId = :remoteId LIMIT 1")
    suspend fun find(seriesId: Long, remoteId: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY uploadDate DESC LIMIT 1")
    suspend fun newest(seriesId: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND watched = 0 ORDER BY number ASC, name COLLATE NOCASE ASC LIMIT 1")
    suspend fun firstUnwatched(seriesId: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes")
    suspend fun all(): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: EpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET watched = :watched, watchedAt = :at WHERE id = :id")
    suspend fun setWatched(id: Long, watched: Boolean, at: Long)

    @Query("UPDATE episodes SET watched = :watched, watchedAt = :at WHERE id IN (:ids)")
    suspend fun setWatchedMany(ids: List<Long>, watched: Boolean, at: Long)

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteForSeries(seriesId: Long)

    @Query("SELECT COUNT(*) FROM episodes")
    suspend fun count(): Int
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE episodeId = :episodeId")
    fun observe(episodeId: Long): Flow<ProgressEntity?>

    @Query("SELECT * FROM progress WHERE episodeId = :episodeId")
    suspend fun byEpisode(episodeId: Long): ProgressEntity?

    @Query("SELECT * FROM progress WHERE seriesId = :seriesId")
    suspend fun forSeries(seriesId: Long): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE seriesId = :seriesId")
    fun observeForSeries(seriesId: Long): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE durationMs > 0 AND positionMs > 0 ORDER BY updatedAt DESC LIMIT 40")
    fun observeRecent(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress")
    suspend fun all(): List<ProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(progress: List<ProgressEntity>)

    @Query("DELETE FROM progress WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: Long)

    @Query("DELETE FROM progress")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT 500")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history")
    suspend fun all(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<HistoryEntity>)

    @Query("DELETE FROM history WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun all(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE categories SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM series_categories WHERE categoryId = :categoryId")
    suspend fun countFor(categoryId: Long): Int

    @Query("SELECT * FROM series_categories")
    suspend fun allLinks(): List<SeriesCategoryCrossRef>

    @Query("SELECT * FROM series_categories")
    fun observeLinks(): Flow<List<SeriesCategoryCrossRef>>

    @Query("SELECT categoryId FROM series_categories WHERE seriesId = :seriesId")
    suspend fun categoriesFor(seriesId: Long): List<Long>

    @Query("SELECT categoryId, COUNT(*) AS count FROM series_categories GROUP BY categoryId")
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query("DELETE FROM series_categories WHERE seriesId = :seriesId")
    suspend fun clearForSeries(seriesId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(links: List<SeriesCategoryCrossRef>)
}

@Dao
interface UpdateDao {
    @Query("SELECT * FROM update_entries ORDER BY discoveredAt DESC")
    fun observeAll(): Flow<List<UpdateEntryEntity>>

    @Query("SELECT * FROM update_entries")
    suspend fun all(): List<UpdateEntryEntity>

    @Query("SELECT COUNT(*) FROM update_entries WHERE seen = 0")
    fun observeUnseenCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: UpdateEntryEntity): Long

    @Query("UPDATE update_entries SET seen = 1")
    suspend fun markAllSeen()

    @Query("DELETE FROM update_entries")
    suspend fun clear()
}
