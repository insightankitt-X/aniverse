package app.anilab.local.data.source

import app.anilab.local.data.db.ExtensionDao
import app.anilab.local.data.db.RepoDao
import app.anilab.local.domain.model.SourceRef
import app.anilab.source.api.Source
import app.anilab.source.runtime.SourceRuntime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only place that turns installed extensions into usable [Source]s. Keeps a short-lived
 * descriptor cache so a screen does not pay an IPC round trip per recomposition.
 */
@Singleton
class SourceRegistry @Inject constructor(
    private val runtime: SourceRuntime,
    private val extensionDao: ExtensionDao,
    private val repoDao: RepoDao,
) {
    data class Handle(val ref: SourceRef, val source: Source)

    private data class Cached(val sources: List<Source>, val at: Long)

    private val cache = LinkedHashMap<String, Cached>()

    suspend fun enabled(): List<Handle> {
        val extensions = extensionDao.enabledInstalled()
        if (extensions.isEmpty()) return emptyList()
        val trustedRepoIds = repoDao.all().filter { it.trusted }.map { it.id }.toSet()
        return extensions.flatMap { extension ->
            val trusted = extension.repoId != null && extension.repoId in trustedRepoIds
            val sources = sourcesOf(extension.pkg)
            sources.map { source ->
                Handle(
                    ref = SourceRef(
                        pkg = extension.pkg,
                        sourceId = source.sourceId,
                        name = source.name,
                        lang = source.lang,
                        nsfw = extension.nsfw,
                        enabled = extension.enabled,
                        trusted = trusted,
                    ),
                    source = source,
                )
            }
        }
    }

    suspend fun handle(pkg: String, sourceId: Long): Handle? {
        val extension = extensionDao.byPkg(pkg) ?: return null
        if (!extension.installed || !extension.enabled) return null
        val source = sourcesOf(pkg).firstOrNull { it.sourceId == sourceId } ?: return null
        val trusted = extension.repoId?.let { id -> repoDao.byId(id)?.trusted } ?: false
        return Handle(
            ref = SourceRef(pkg, sourceId, source.name, source.lang, extension.nsfw, extension.enabled, trusted),
            source = source,
        )
    }

    /** Drops the cached sources, e.g. after an extension is installed, updated or removed. */
    fun invalidate(pkg: String? = null) {
        if (pkg == null) cache.clear() else cache.remove(pkg)
        if (pkg != null) runtime.reset(pkg)
    }

    private suspend fun sourcesOf(pkg: String): List<Source> {
        val now = System.currentTimeMillis()
        cache[pkg]?.let { cached -> if (now - cached.at < CACHE_TTL_MS) return cached.sources }
        val sources = runCatching { runtime.sources(pkg) }.getOrElse { emptyList() }
        cache[pkg] = Cached(sources, now)
        return sources
    }

    private companion object {
        const val CACHE_TTL_MS = 60_000L
    }
}
