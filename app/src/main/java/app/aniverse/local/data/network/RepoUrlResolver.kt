package app.aniverse.local.data.network

import java.net.URI

sealed interface RepoUrlResolution {
    data class Resolved(val indexUrl: String, val displayName: String) : RepoUrlResolution

    data object Empty : RepoUrlResolution

    data object NotHttps : RepoUrlResolution

    data object Malformed : RepoUrlResolution
}

/**
 * Turns whatever the user pasted into an index URL: a folder URL, a full index URL, or a URL with
 * a trailing `index.min.json`. Only https is ever accepted.
 */
object RepoUrlResolver {
    const val INDEX_FILE = "index.min.json"

    /** Folder Aniyomi-style repositories publish their APKs in. */
    const val APK_FOLDER = "apk"

    fun resolve(input: String): RepoUrlResolution {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return RepoUrlResolution.Empty

        val candidate = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> return RepoUrlResolution.NotHttps
            trimmed.contains("://") -> return RepoUrlResolution.NotHttps
            else -> "https://$trimmed"
        }

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return RepoUrlResolution.Malformed
        if (!uri.scheme.equals("https", ignoreCase = true)) return RepoUrlResolution.NotHttps
        if (uri.host.isNullOrBlank()) return RepoUrlResolution.Malformed

        val withoutQuery = candidate.substringBefore('?').substringBefore('#')
        val lastSegment = withoutQuery.substringAfterLast('/', "")
        val indexUrl = if (lastSegment.endsWith(".json", ignoreCase = true)) {
            withoutQuery
        } else {
            "${withoutQuery.trimEnd('/')}/$INDEX_FILE"
        }

        return RepoUrlResolution.Resolved(indexUrl, displayName(indexUrl))
    }

    fun parentUrl(indexUrl: String): String = indexUrl.substringBefore('?').substringBeforeLast('/')

    fun displayName(indexUrl: String): String {
        val uri = runCatching { URI(indexUrl) }.getOrNull() ?: return indexUrl
        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        return when {
            uri.host.equals("raw.githubusercontent.com", ignoreCase = true) && segments.size >= 2 ->
                "${segments[0]}/${segments[1]}"
            segments.size >= 2 -> segments[segments.size - 2]
            uri.host.isNullOrBlank() -> indexUrl
            else -> uri.host
        }
    }

    /**
     * Resolves an index-relative apk/icon path against the index URL's folder.
     *
     * Aniyomi-style repositories (Keiyoushi, Yuzono, ...) list bare file names in the index while
     * the files themselves live in an `apk/` or `icon/` folder next to it. [bareFolder] supplies
     * that folder for bare names; it is ignored when the reference already contains a path.
     */
    fun resolveFile(indexUrl: String, reference: String, bareFolder: String? = null): String? {
        val trimmed = reference.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            return trimmed
        }
        val base = parentUrl(indexUrl)
        val cleaned = trimmed.removePrefix("./").removePrefix("/")
        val path = if (bareFolder != null && !cleaned.contains('/')) "$bareFolder/$cleaned" else cleaned
        return "$base/$path"
    }

    /**
     * The same file in the other repository layout: `repo/apk/x.apk` <-> `repo/x.apk`.
     * Used as a one-shot retry when the repository disagrees with the index.
     */
    fun alternateFileLayout(url: String): String? {
        val marker = "/$APK_FOLDER/"
        val base = url.substringBefore('?').substringBefore('#')
        val query = if (url.contains('?')) "?" + url.substringAfter('?') else ""
        return when {
            base.contains(marker) -> base.replaceFirst(marker, "/") + query
            else -> {
                val slash = base.lastIndexOf('/')
                if (slash <= "https://".length) {
                    null
                } else {
                    base.substring(0, slash) + marker + base.substring(slash + 1) + query
                }
            }
        }
    }
}
