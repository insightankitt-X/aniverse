package app.aniverse.local.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.aniverse.local.R
import app.aniverse.local.data.db.DownloadDao
import app.aniverse.local.data.db.EpisodeDao
import app.aniverse.local.data.db.SeriesDao
import app.aniverse.local.data.media.DownloadStorage
import app.aniverse.local.data.source.userMessage
import app.aniverse.local.data.toDomain
import app.aniverse.local.domain.model.DownloadState
import app.aniverse.local.domain.repository.BrowseRepository
import app.aniverse.local.domain.repository.PlayableVideo
import app.aniverse.local.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.roundToInt

/** One WorkManager request per episode, resumable through a `.part` file and HTTP range requests. */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val browseRepository: BrowseRepository,
    private val storage: DownloadStorage,
    private val settings: SettingsRepository,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val episodeId = inputData.getLong(KEY_EPISODE_ID, -1L)
        if (episodeId <= 0L) return Result.failure()

        val record = downloadDao.byEpisode(episodeId) ?: return Result.failure()
        if (record.state == DownloadState.COMPLETED.name && record.filePath != null && storage.exists(record.filePath)) {
            return Result.success()
        }
        val seriesRow = seriesDao.byId(record.seriesId) ?: return fail(episodeId, "This series is no longer in your library")
        val episodeRow = episodeDao.byId(episodeId) ?: return fail(episodeId, "This episode is no longer available")

        val preferences = settings.current()
        // The parallel limit is enforced here because WorkManager has its own scheduler.
        val running = downloadDao.active().count { it.episodeId != episodeId && it.state == DownloadState.DOWNLOADING.name }
        if (running >= preferences.downloadParallelLimit) return Result.retry()

        val label = "${seriesRow.title} - ${episodeRow.name}"
        setForeground(foreground(label, 0, indeterminate = true))
        downloadDao.updateState(episodeId, DownloadState.DOWNLOADING.name, 0f, 0L, 0L, null, System.currentTimeMillis())

        val videos = browseRepository.videos(seriesRow.toDomain(), episodeRow.toDomain()).getOrElse { error ->
            return fail(episodeId, error.userMessage())
        }
        val video = pickProgressive(videos, preferences.playerPreferredQuality)
            ?: return fail(episodeId, "This source only offers streamed video, which cannot be saved offline")

        val part = storage.createPart(seriesRow.title, episodeRow.name, EXTENSION)
            ?: return fail(episodeId, "Could not create the download file. Check the download folder.")
        val alreadyDownloaded = part.length()

        return try {
            val request = Request.Builder()
                .url(video.url)
                .apply { video.headers.forEach { (key, value) -> header(key, value) } }
                .apply { if (alreadyDownloaded > 0L) header("Range", "bytes=$alreadyDownloaded-") }
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return fail(episodeId, "The download was refused (HTTP ${response.code})")
                }
                val body = response.body
                    ?: return fail(episodeId, "The download was empty")
                val appending = response.code == 206 && alreadyDownloaded > 0L
                val total = body.contentLength().let { length -> if (length > 0) length + (if (appending) alreadyDownloaded else 0L) else 0L }
                if (total > 0L && storage.freeBytes() < total * 2) {
                    return fail(episodeId, "Not enough free storage for this episode")
                }

                val output = part.openAppend(appending)
                    ?: return fail(episodeId, "Could not write to the download folder")
                output.use { sink ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var written = if (appending) alreadyDownloaded else 0L
                        var lastReport = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            sink.write(buffer, 0, read)
                            written += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport > PROGRESS_INTERVAL_MS) {
                                lastReport = now
                                val fraction = if (total > 0) (written.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                                downloadDao.updateState(
                                    episodeId,
                                    DownloadState.DOWNLOADING.name,
                                    fraction,
                                    written,
                                    total,
                                    null,
                                    now,
                                )
                                setForeground(foreground(label, (fraction * 100).roundToInt(), indeterminate = total <= 0L))
                            }
                        }
                    }
                }
                body.close()
            }

            val reference = storage.commit(part, seriesRow.title, episodeRow.name, EXTENSION)
            if (reference == null) {
                part.delete()
                return fail(episodeId, "The finished file could not be saved")
            }
            val finalReference = storage.reference(reference)
            downloadDao.markCompleted(episodeId, finalReference, System.currentTimeMillis())
            downloadDao.setQuality(episodeId, video.quality)
            setForeground(foreground(label, 100, indeterminate = false, finished = true))
            Result.success()
        } catch (e: CancellationException) {
            downloadDao.updateState(
                episodeId,
                DownloadState.PAUSED.name,
                0f,
                0L,
                0L,
                null,
                System.currentTimeMillis(),
            )
            throw e
        } catch (e: IOException) {
            fail(episodeId, "The download was interrupted. It will resume where it stopped.")
        } catch (e: Exception) {
            fail(episodeId, "The download failed. Try again.")
        }
    }

    private fun pickProgressive(videos: List<PlayableVideo>, preferred: String?): PlayableVideo? {
        val progressive = videos.filter { it.kind == "MP4" }
        if (progressive.isEmpty()) return null
        preferred?.let { quality -> progressive.firstOrNull { it.quality == quality }?.let { return it } }
        return progressive.maxByOrNull { video -> video.quality.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }

    private suspend fun fail(episodeId: Long, message: String): Result {
        downloadDao.updateState(
            episodeId,
            DownloadState.FAILED.name,
            0f,
            0L,
            0L,
            message,
            System.currentTimeMillis(),
        )
        return Result.failure()
    }

    private fun foreground(label: String, progress: Int, indeterminate: Boolean, finished: Boolean = false): ForegroundInfo {
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(label)
            .setContentText(if (finished) "Download complete" else "Downloading")
            .setOngoing(!finished)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .build()
        val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(NOTIFICATION_ID, notification, type)
    }

    private fun ensureChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val KEY_EPISODE_ID = "episodeId"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 4101
        private const val EXTENSION = "mp4"
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L
    }
}
