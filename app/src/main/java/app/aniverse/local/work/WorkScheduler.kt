package app.aniverse.local.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleLibraryUpdates(intervalHours: Int, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(
            intervalHours.coerceIn(1, 24).toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_LIBRARY_UPDATES,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelLibraryUpdates() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_LIBRARY_UPDATES)
    }

    fun enqueueDownload(episodeId: Long, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_EPISODE_ID to episodeId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_DOWNLOAD)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            downloadWorkName(episodeId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelDownload(episodeId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(downloadWorkName(episodeId))
    }

    fun cancelAllDownloads() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_DOWNLOAD)
    }

    companion object {
        const val WORK_LIBRARY_UPDATES = "library-updates"
        const val TAG_DOWNLOAD = "episode-download"

        fun downloadWorkName(episodeId: Long): String = "download-$episodeId"
    }
}
