package app.aniverse.local.ui.components

import java.util.concurrent.TimeUnit

fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun formatRemaining(positionMs: Long, durationMs: Long): String {
    val remaining = (durationMs - positionMs).coerceAtLeast(0L)
    return "${formatDuration(remaining)} left"
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1024) "%.1f GB".format(megabytes / 1024) else "%.0f MB".format(megabytes)
}

fun formatProgressPercent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%"
