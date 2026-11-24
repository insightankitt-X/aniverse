package app.anilab.local

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.anilab.local.domain.repository.DownloadRepository
import app.anilab.local.domain.repository.SettingsRepository
import app.anilab.local.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AniLabApp : Application(), Configuration.Provider {
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
