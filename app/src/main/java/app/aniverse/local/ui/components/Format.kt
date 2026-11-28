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

/** "4h ago" style label used by Home and Schedule. */
fun formatRelativeTime(timestampMs: Long, now: Long = System.currentTimeMillis()): String {
    if (timestampMs <= 0L) return ""
    val minutes = ((now - timestampMs) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 24L * 60L -> "${minutes / 60L}h ago"
        minutes < 7L * 24L * 60L -> "${minutes / (24L * 60L)}d ago"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(timestampMs))
    }
}

/** Clock time for the schedule day list. */
fun formatClock(timestampMs: Long): String =
    if (timestampMs <= 0L) "" else java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestampMs))
