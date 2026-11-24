package app.anilab.local

import app.anilab.local.data.backup.BackupFile
import app.anilab.local.data.backup.CategoryBackup
import app.anilab.local.data.backup.HistoryBackup
import app.anilab.local.data.backup.ProgressBackup
import app.anilab.local.data.backup.SeriesBackup
import app.anilab.local.data.backup.SeriesRef
import app.anilab.local.data.backup.SettingsBackup
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun sample(): BackupFile {
        val ref = SeriesRef(sourcePkg = "eu.example.anime", sourceId = 7L, remoteId = "/series/1")
        return BackupFile(
            createdAt = 1_700_000_000_000L,
            series = listOf(
                SeriesBackup(ref = ref, title = "Sample", genres = listOf("Action"), inLibrary = true),
            ),
            categories = listOf(CategoryBackup(name = "Seasonal", sortOrder = 0, series = listOf(ref))),
            progress = listOf(
                ProgressBackup(
                    series = ref,
                    episodeRemoteId = "/ep/1",
                    positionMs = 60_000L,
                    durationMs = 1_440_000L,
                    updatedAt = 1_700_000_000_000L,
                ),
            ),
            history = listOf(HistoryBackup(series = ref, episodeRemoteId = "/ep/1", watchedAt = 1_700_000_000_000L)),
            settings = SettingsBackup(themeMode = "DARK", pureBlack = true),
        )
    }

    @Test
    fun roundTripsEverySection() {
        val original = sample()
        val encoded = json.encodeToString(BackupFile.serializer(), original)
        val decoded = json.decodeFromString(BackupFile.serializer(), encoded)

        assertEquals(BackupFile.CURRENT_VERSION, decoded.version)
        assertEquals(original.series, decoded.series)
        assertEquals(original.categories, decoded.categories)
        assertEquals(original.progress, decoded.progress)
        assertEquals(original.history, decoded.history)
        assertEquals("DARK", decoded.settings?.themeMode)
        assertTrue(decoded.settings?.pureBlack == true)
    }

    @Test
    fun missingSectionsDefaultToEmpty() {
        val decoded = json.decodeFromString(BackupFile.serializer(), "{\"version\":1}")
        assertEquals(BackupFile.CURRENT_VERSION, decoded.version)
        assertTrue(decoded.series.isEmpty())
        assertTrue(decoded.repositories.isEmpty())
        assertEquals(null, decoded.settings)
    }

    @Test
    fun unknownKeysAreIgnoredForForwardCompatibility() {
        val payload = "{\"version\":1,\"futureField\":{\"nested\":true},\"series\":[]}"
        val decoded = json.decodeFromString(BackupFile.serializer(), payload)
        assertTrue(decoded.series.isEmpty())
    }
}
