package eu.kanade.tachiyomi

/**
 * Info about the installed host app (NOT THE EXTENSION!).
 * Can be useful for temporary fixes, preferences, header values, logging, etc.
 */
object AppInfo {
    /**
     * Version code of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 13
     */
    fun getVersionCode(): Int = AniyomiHost.appVersionCode()

    /**
     * Version name of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 13
     */
    fun getVersionName(): String = AniyomiHost.appVersionName()

    /**
     * Image MIME types the app can display. AniVerse renders covers with Coil, which handles all
     * common raster formats.
     *
     * @since extension-lib 1.5
     */
    fun getSupportedImageMimeTypes(): List<String> = listOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "image/bmp",
    )
}
