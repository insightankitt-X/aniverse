package app.aniverse.local.data.network

import kotlinx.serialization.Serializable

/**
 * One entry of an extension repository index. Every field is optional here because the parser has
 * to tolerate partial indexes; validation happens in [RepoIndexParser].
 */
@Serializable
data class RepoExtensionDto(
    val name: String? = null,
    val pkg: String? = null,
    val apk: String? = null,
    val lang: String? = null,
    val code: Int? = null,
    val version: String? = null,
    val nsfw: Int? = null,
    val icon: String? = null,
    val description: String? = null,
)

/** Parsed, absolute-URL form of an index. */
data class ParsedIndex(
    val name: String,
    val extensions: List<ParsedExtension>,
)

data class ParsedExtension(
    val pkg: String,
    val name: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val nsfw: Boolean,
    val iconUrl: String?,
    val description: String?,
    val apkUrl: String,
)

sealed interface IndexFetchResult {
    data class Success(val index: ParsedIndex) : IndexFetchResult

    data object Unreachable : IndexFetchResult

    data object NotAnIndex : IndexFetchResult
}
