package app.aniverse.local.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    @Query("SELECT * FROM repos ORDER BY pinned DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<RepoEntity>>

    @Query("SELECT * FROM repos ORDER BY pinned DESC, name COLLATE NOCASE ASC")
    suspend fun all(): List<RepoEntity>

    @Query("SELECT * FROM repos WHERE id = :id")
    suspend fun byId(id: Long): RepoEntity?

    @Query("SELECT * FROM repos WHERE indexUrl = :indexUrl LIMIT 1")
    suspend fun byIndexUrl(indexUrl: String): RepoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(repo: RepoEntity): Long

    @Query("UPDATE repos SET lastUpdatedAt = :at, extensionCount = :count, lastError = NULL WHERE id = :id")
    suspend fun markRefreshed(id: Long, at: Long, count: Int)

    @Query("UPDATE repos SET lastError = :error WHERE id = :id")
    suspend fun markError(id: Long, error: String)

    @Query("UPDATE repos SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE repos SET trusted = :trusted WHERE id = :id")
    suspend fun setTrusted(id: Long, trusted: Boolean)

    @Query("DELETE FROM repos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM repos")
    suspend fun count(): Int
}

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extensions WHERE installed = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeInstalled(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions WHERE repoId IS NOT NULL ORDER BY repoId ASC, name COLLATE NOCASE ASC")
    fun observeAvailable(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions WHERE installed = 1 AND enabled = 1")
    fun observeEnabled(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions WHERE installed = 1 AND enabled = 1")
    suspend fun enabledInstalled(): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE installed = 1")
    suspend fun installed(): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE pkg = :pkg")
    suspend fun byPkg(pkg: String): ExtensionEntity?

    @Query("SELECT * FROM extensions WHERE pkg = :pkg")
    fun observeByPkg(pkg: String): Flow<ExtensionEntity?>

    @Query("SELECT * FROM extensions WHERE repoId = :repoId")
    suspend fun forRepo(repoId: Long): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE installed = 1 AND versionCode > installedVersionCode " +
        "AND repoId IN (SELECT id FROM repos WHERE trusted = 1)")
    suspend fun updatableFromTrustedRepos(): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE installed = 1 AND versionCode > installedVersionCode")
    suspend fun updatable(): List<ExtensionEntity>

    @Query("SELECT COUNT(*) FROM extensions WHERE installed = 1 AND versionCode > installedVersionCode")
    fun observeUpdateCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(extension: ExtensionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(extensions: List<ExtensionEntity>)

    @Query("UPDATE extensions SET installed = 1, enabled = :enabled, installedVersionCode = :code, " +
        "installedVersionName = :name, installedAt = :at, failureCount = 0, disabledReason = NULL " +
        "WHERE pkg = :pkg")
    suspend fun markInstalled(pkg: String, code: Int, name: String, at: Long, enabled: Boolean)

    @Query("UPDATE extensions SET installed = 0, enabled = 0, installedVersionCode = 0, " +
        "installedVersionName = '', installedAt = 0 WHERE pkg = :pkg")
    suspend fun markUninstalled(pkg: String)

    @Query("UPDATE extensions SET enabled = :enabled WHERE pkg = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)

    @Query("UPDATE extensions SET nsfw = :nsfw WHERE pkg = :pkg")
    suspend fun setNsfw(pkg: String, nsfw: Boolean)

    @Query("UPDATE extensions SET failureCount = failureCount + 1, disabledReason = :reason WHERE pkg = :pkg")
    suspend fun recordFailure(pkg: String, reason: String)

    @Query("UPDATE extensions SET failureCount = 0, disabledReason = NULL WHERE pkg = :pkg")
    suspend fun clearFailures(pkg: String)

    @Query("UPDATE extensions SET enabled = 0, disabledReason = :reason WHERE pkg = :pkg")
    suspend fun disable(pkg: String, reason: String)

    @Query("DELETE FROM extensions WHERE pkg = :pkg")
    suspend fun delete(pkg: String)

    /** Installed packages that vanished from a refreshed index keep their row but leave the repo. */
    @Query("UPDATE extensions SET repoId = NULL WHERE repoId = :repoId AND pkg NOT IN (:keep)")
    suspend fun detachMissing(repoId: Long, keep: List<String>)

    @Query("DELETE FROM extensions WHERE repoId = :repoId AND installed = 0 AND pkg NOT IN (:keep)")
    suspend fun deleteMissingAvailable(repoId: Long, keep: List<String>)

    @Query("DELETE FROM extensions WHERE repoId = :repoId AND installed = 0")
    suspend fun deleteAllAvailable(repoId: Long)

    @Query("SELECT COUNT(*) FROM extensions WHERE repoId = :repoId")
    suspend fun countForRepo(repoId: Long): Int
}

@Dao
interface SourceCacheDao {
    @Query("SELECT * FROM source_cache WHERE cacheKey = :key")
    suspend fun byKey(key: String): SourceCacheEntity?

    @Query("SELECT * FROM source_cache WHERE cacheKey = :key")
    fun observe(key: String): Flow<SourceCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SourceCacheEntity)

    @Query("DELETE FROM source_cache WHERE sourcePkg = :pkg")
    suspend fun clearForSource(pkg: String)

    @Query("DELETE FROM source_cache WHERE updatedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM source_cache")
    suspend fun clear()

    @Query("SELECT MAX(updatedAt) FROM source_cache WHERE sourcePkg = :pkg AND kind = :kind")
    suspend fun lastUpdated(pkg: String, kind: String): Long?
}
