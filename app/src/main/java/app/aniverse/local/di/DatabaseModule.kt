package app.aniverse.local.di

import android.content.Context
import androidx.room.Room
import app.aniverse.local.data.db.AniVerseDatabase
import app.aniverse.local.data.db.CategoryDao
import app.aniverse.local.data.db.DownloadDao
import app.aniverse.local.data.db.EpisodeDao
import app.aniverse.local.data.db.ExtensionDao
import app.aniverse.local.data.db.HistoryDao
import app.aniverse.local.data.db.ProgressDao
import app.aniverse.local.data.db.RepoDao
import app.aniverse.local.data.db.SeriesDao
import app.aniverse.local.data.db.SourceCacheDao
import app.aniverse.local.data.db.UpdateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AniVerseDatabase =
        Room.databaseBuilder(context, AniVerseDatabase::class.java, AniVerseDatabase.NAME)
            .addMigrations(*AniVerseDatabase.MIGRATIONS)
            .build()

    @Provides
    fun repoDao(database: AniVerseDatabase): RepoDao = database.repoDao()

    @Provides
    fun extensionDao(database: AniVerseDatabase): ExtensionDao = database.extensionDao()

    @Provides
    fun seriesDao(database: AniVerseDatabase): SeriesDao = database.seriesDao()

    @Provides
    fun episodeDao(database: AniVerseDatabase): EpisodeDao = database.episodeDao()

    @Provides
    fun progressDao(database: AniVerseDatabase): ProgressDao = database.progressDao()

    @Provides
    fun historyDao(database: AniVerseDatabase): HistoryDao = database.historyDao()

    @Provides
    fun categoryDao(database: AniVerseDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun downloadDao(database: AniVerseDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun sourceCacheDao(database: AniVerseDatabase): SourceCacheDao = database.sourceCacheDao()

    @Provides
    fun updateDao(database: AniVerseDatabase): UpdateDao = database.updateDao()
}
