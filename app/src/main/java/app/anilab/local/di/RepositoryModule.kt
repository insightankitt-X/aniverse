package app.anilab.local.di

import app.anilab.local.data.network.AndroidConnectivityRepository
import app.anilab.local.data.prefs.DataStoreSettingsRepository
import app.anilab.local.data.repo.RoomBrowseRepository
import app.anilab.local.data.repo.RoomDownloadRepository
import app.anilab.local.data.repo.RoomExtensionRepository
import app.anilab.local.data.repo.RoomHistoryRepository
import app.anilab.local.data.repo.RoomLibraryRepository
import app.anilab.local.data.repo.RoomProgressRepository
import app.anilab.local.data.repo.RoomRepoRepository
import app.anilab.local.data.backup.JsonBackupRepository
import app.anilab.local.domain.repository.BackupRepository
import app.anilab.local.domain.repository.BrowseRepository
import app.anilab.local.domain.repository.ConnectivityRepository
import app.anilab.local.domain.repository.DownloadRepository
import app.anilab.local.domain.repository.ExtensionRepository
import app.anilab.local.domain.repository.HistoryRepository
import app.anilab.local.domain.repository.LibraryRepository
import app.anilab.local.domain.repository.ProgressRepository
import app.anilab.local.domain.repository.RepoRepository
import app.anilab.local.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun repoRepository(impl: RoomRepoRepository): RepoRepository

    @Binds
    @Singleton
    abstract fun extensionRepository(impl: RoomExtensionRepository): ExtensionRepository

    @Binds
    @Singleton
    abstract fun browseRepository(impl: RoomBrowseRepository): BrowseRepository

    @Binds
    @Singleton
    abstract fun libraryRepository(impl: RoomLibraryRepository): LibraryRepository

    @Binds
    @Singleton
    abstract fun historyRepository(impl: RoomHistoryRepository): HistoryRepository

    @Binds
    @Singleton
    abstract fun progressRepository(impl: RoomProgressRepository): ProgressRepository

    @Binds
    @Singleton
    abstract fun downloadRepository(impl: RoomDownloadRepository): DownloadRepository

    @Binds
    @Singleton
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun backupRepository(impl: JsonBackupRepository): BackupRepository

    @Binds
    @Singleton
    abstract fun connectivityRepository(impl: AndroidConnectivityRepository): ConnectivityRepository
}
