package app.anilab.local.di

import android.content.Context
import androidx.room.Room
import app.anilab.local.data.db.AniLabDatabase
import app.anilab.local.data.db.CategoryDao
import app.anilab.local.data.db.DownloadDao
import app.anilab.local.data.db.EpisodeDao
import app.anilab.local.data.db.ExtensionDao
import app.anilab.local.data.db.HistoryDao
import app.anilab.local.data.db.ProgressDao
import app.anilab.local.data.db.RepoDao
import app.anilab.local.data.db.SeriesDao
import app.anilab.local.data.db.SourceCacheDao
import app.anilab.local.data.db.UpdateDao
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
    fun database(@ApplicationContext context: Context): AniLabDatabase =
        Room.databaseBuilder(context, AniLabDatabase::class.java, AniLabDatabase.NAME)
            .addMigrations(*AniLabDatabase.MIGRATIONS)
            .build()

    @Provides
    fun repoDao(database: AniLabDatabase): RepoDao = database.repoDao()

    @Provides
    fun extensionDao(database: AniLabDatabase): ExtensionDao = database.extensionDao()

    @Provides
    fun seriesDao(database: AniLabDatabase): SeriesDao = database.seriesDao()

    @Provides
    fun episodeDao(database: AniLabDatabase): EpisodeDao = database.episodeDao()

    @Provides
    fun progressDao(database: AniLabDatabase): ProgressDao = database.progressDao()

    @Provides
    fun historyDao(database: AniLabDatabase): HistoryDao = database.historyDao()

    @Provides
    fun categoryDao(database: AniLabDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun downloadDao(database: AniLabDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun sourceCacheDao(database: AniLabDatabase): SourceCacheDao = database.sourceCacheDao()

    @Provides
    fun updateDao(database: AniLabDatabase): UpdateDao = database.updateDao()
}
