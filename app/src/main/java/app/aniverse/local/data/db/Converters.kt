package app.aniverse.local.data.db

import androidx.room.TypeConverter

/** Genres are stored as a single delimited column; the separator cannot appear in a genre name. */
class Converters {
    @TypeConverter
    fun fromGenres(value: List<String>?): String =
        value.orEmpty().map { it.replace(SEPARATOR, " ") }.joinToString(SEPARATOR)

    @TypeConverter
    fun toGenres(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList() else value.split(SEPARATOR).filter { it.isNotBlank() }

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
