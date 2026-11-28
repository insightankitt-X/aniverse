package app.aniverse.local

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import app.aniverse.local.domain.repository.DownloadRepository
import app.aniverse.local.domain.repository.SettingsRepository
import app.aniverse.local.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AniVerseApp : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var workScheduler: WorkScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * One shared loader: a short crossfade, a bounded memory cache and a disk cache so scrolling
     * back through a row does not decode the same poster again.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(180)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024L * 1024L)
                .build()
        }
        .build()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // Downloads that lost their file show up as Missing instead of silently vanishing.
            runCatching { downloadRepository.reconcile() }
            val settings = settingsRepository.current()
            workScheduler.scheduleLibraryUpdates(settings.updateIntervalHours, settings.wifiOnlyUpdates)
        }
    }
}
