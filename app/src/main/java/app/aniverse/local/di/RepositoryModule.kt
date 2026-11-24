package app.aniverse.local.di

import app.aniverse.local.data.backup.JsonBackupRepository
import app.aniverse.local.data.network.AndroidConnectivityRepository
import app.aniverse.local.data.prefs.DataStoreSettingsRepository
import app.aniverse.local.data.repo.RoomBrowseRepository
import app.aniverse.local.data.repo.RoomCategoryMembership
import app.aniverse.local.data.repo.RoomDownloadRepository
import app.aniverse.local.data.repo.RoomExtensionRepository
import app.aniverse.local.data.repo.RoomHistoryRepository
import app.aniverse.local.data.repo.RoomLibraryRepository
import app.aniverse.local.data.repo.RoomProgressRepository
import app.aniverse.local.data.repo.RoomRepoRepository
import app.aniverse.local.domain.repository.BackupRepository
import app.aniverse.local.domain.repository.BrowseRepository
import app.aniverse.local.domain.repository.CategoryMembership
import app.aniverse.local.domain.repository.ConnectivityRepository
import app.aniverse.local.domain.repository.DownloadRepository
import app.aniverse.local.domain.repository.ExtensionRepository
import app.aniverse.local.domain.repository.HistoryRepository
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.domain.repository.ProgressRepository
import app.aniverse.local.domain.repository.RepoRepository
import app.aniverse.local.domain.repository.SettingsRepository
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

    @Binds
    @Singleton
    abstract fun categoryMembership(impl: RoomCategoryMembership): CategoryMembership
}
