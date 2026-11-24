package app.anilab.local.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema is exported to `app/schemas` at build time. There is deliberately no destructive
 * fallback: a missing migration must fail loudly in development rather than wipe a user's
 * library, history and download records.
 */
@Database(
    entities = [
        RepoEntity::class,
        ExtensionEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        ProgressEntity::class,
        HistoryEntity::class,
        CategoryEntity::class,
        SeriesCategoryCrossRef::class,
        DownloadEntity::class,
        SourceCacheEntity::class,
        UpdateEntryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AniLabDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao

    abstract fun extensionDao(): ExtensionDao

    abstract fun seriesDao(): SeriesDao

    abstract fun episodeDao(): EpisodeDao

    abstract fun progressDao(): ProgressDao

    abstract fun historyDao(): HistoryDao

    abstract fun categoryDao(): CategoryDao

    abstract fun downloadDao(): DownloadDao

    abstract fun sourceCacheDao(): SourceCacheDao

    abstract fun updateDao(): UpdateDao

    companion object {
        const val NAME = "anilab.db"
        const val VERSION = 2

        /**
         * v1 shipped without repository error state, without the library-added timestamp and
         * without extension install timestamps. All three are additive, so nothing is lost.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE repos ADD COLUMN lastError TEXT")
                db.execSQL("ALTER TABLE series ADD COLUMN addedToLibraryAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE extensions ADD COLUMN installedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE series SET addedToLibraryAt = lastUpdatedAt " +
                        "WHERE inLibrary = 1 AND addedToLibraryAt = 0",
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
