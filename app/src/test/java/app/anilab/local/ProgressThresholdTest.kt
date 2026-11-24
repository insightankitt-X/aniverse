package app.anilab.local

import app.anilab.local.domain.model.PlaybackThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThresholdTest {

    @Test
    fun fractionIsClampedAndSafe() {
        assertEquals(0f, PlaybackThresholds.fraction(1_000L, 0L), 0.0001f)
        assertEquals(0.5f, PlaybackThresholds.fraction(50L, 100L), 0.0001f)
        assertEquals(1f, PlaybackThresholds.fraction(150L, 100L), 0.0001f)
    }

    @Test
    fun watchedStartsAtNinetyPercent() {
        assertFalse(PlaybackThresholds.isWatched(89_000L, 100_000L))
        assertTrue(PlaybackThresholds.isWatched(90_000L, 100_000L))
        assertFalse(PlaybackThresholds.isWatched(90_000L, 0L))
    }

    @Test
    fun continueWatchingExcludesTheEdges() {
        assertFalse(PlaybackThresholds.isContinueWatching(5_000L, 100_000L))
        assertTrue(PlaybackThresholds.isContinueWatching(5_100L, 100_000L))
        assertTrue(PlaybackThresholds.isContinueWatching(94_999L, 100_000L))
        assertFalse(PlaybackThresholds.isContinueWatching(95_000L, 100_000L))
    }

    @Test
    fun remainingNeverGoesNegative() {
        assertEquals(1_000L, PlaybackThresholds.remainingMs(9_000L, 10_000L))
        assertEquals(0L, PlaybackThresholds.remainingMs(12_000L, 10_000L))
    }
}
