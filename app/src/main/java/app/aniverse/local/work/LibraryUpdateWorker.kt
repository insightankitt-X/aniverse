package app.aniverse.local.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.aniverse.local.domain.repository.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodic library refresh. Failures per source are kept in the report, never thrown away. */
@HiltWorker
class LibraryUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val libraryRepository: LibraryRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val report = libraryRepository.refreshLibrary { _, _ -> }
        if (report.checked == 0) Result.success()
        else if (report.failures.isNotEmpty() && report.newEpisodes == 0) Result.retry()
        else Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
