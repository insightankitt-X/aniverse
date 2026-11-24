package app.aniverse.local.data.repo

import app.aniverse.local.data.db.ExtensionDao
import app.aniverse.local.data.db.RepoDao
import app.aniverse.local.data.db.RepoEntity
import app.aniverse.local.data.network.IndexFetchResult
import app.aniverse.local.data.network.ParsedIndex
import app.aniverse.local.data.network.RepoIndexFetcher
import app.aniverse.local.data.network.RepoUrlResolution
import app.aniverse.local.data.network.RepoUrlResolver
import app.aniverse.local.data.toDomain
import app.aniverse.local.data.toEntity
import app.aniverse.local.domain.model.RepoExtension
import app.aniverse.local.domain.model.RepoSummary
import app.aniverse.local.domain.repository.AddRepoResult
import app.aniverse.local.domain.repository.RepoInputError
import app.aniverse.local.domain.repository.RepoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRepoRepository @Inject constructor(
    private val repoDao: RepoDao,
    private val extensionDao: ExtensionDao,
    private val fetcher: RepoIndexFetcher,
) : RepoRepository {

    override fun observeRepos(): Flow<List<RepoSummary>> =
        repoDao.observeAll().map { repos -> repos.map { it.toDomain() } }

    override suspend fun addRepo(url: String): AddRepoResult {
        val resolution = RepoUrlResolver.resolve(url)
        val resolved = when (resolution) {
            RepoUrlResolution.Empty -> return AddRepoResult.Rejected(RepoInputError.EMPTY)
            RepoUrlResolution.NotHttps -> return AddRepoResult.Rejected(RepoInputError.NOT_HTTPS)
            RepoUrlResolution.Malformed -> return AddRepoResult.Rejected(RepoInputError.NOT_HTTPS)
            is RepoUrlResolution.Resolved -> resolution
        }

        if (repoDao.byIndexUrl(resolved.indexUrl) != null) {
            return AddRepoResult.Rejected(RepoInputError.ALREADY_ADDED)
        }

        return when (val fetched = fetcher.fetch(resolved.indexUrl)) {
            IndexFetchResult.Unreachable -> AddRepoResult.Rejected(RepoInputError.UNREACHABLE)
            IndexFetchResult.NotAnIndex -> AddRepoResult.Rejected(RepoInputError.NOT_AN_INDEX)
            is IndexFetchResult.Success -> {
                val repo = RepoEntity(
                    name = fetched.index.name,
                    url = resolved.indexUrl,
                    indexUrl = resolved.indexUrl,
                    extensionCount = fetched.index.extensions.size,
                    lastUpdatedAt = System.currentTimeMillis(),
                    trusted = false,
                )
                val id = repoDao.insert(repo)
                applyIndex(id, fetched.index)
                AddRepoResult.Added(repo.copy(id = id).toDomain())
            }
        }
    }

    override suspend fun refresh(repoId: Long): Boolean {
        val repo = repoDao.byId(repoId) ?: return false
        return when (val fetched = fetcher.fetch(repo.indexUrl)) {
            is IndexFetchResult.Success -> {
                applyIndex(repoId, fetched.index)
                true
            }

            IndexFetchResult.Unreachable -> {
                repoDao.markError(repoId, "Couldn't refresh - showing cached")
                false
            }

            IndexFetchResult.NotAnIndex -> {
                repoDao.markError(repoId, "This URL no longer serves a valid index")
                false
            }
        }
    }

    override suspend fun refreshAll(): Int {
        var failures = 0
        repoDao.all().forEach { repo -> if (!refresh(repo.id)) failures++ }
        return failures
    }

    override suspend fun setPinned(repoId: Long, pinned: Boolean) = repoDao.setPinned(repoId, pinned)

    override suspend fun setTrusted(repoId: Long, trusted: Boolean) = repoDao.setTrusted(repoId, trusted)

    override suspend fun delete(repoId: Long) {
        // Available entries disappear with the index; installed extensions are untouched (FK SET_NULL).
        extensionDao.deleteAllAvailable(repoId)
        repoDao.delete(repoId)
    }

    override suspend fun availableExtensions(repoId: Long): List<RepoExtension> =
        extensionDao.forRepo(repoId).map { it.toAvailable() }

    private suspend fun applyIndex(repoId: Long, index: ParsedIndex) {
        val existing = extensionDao.forRepo(repoId).associateBy { it.pkg }
        val entities = index.extensions.map { parsed -> parsed.toEntity(repoId, existing[parsed.pkg]) }
        if (entities.isNotEmpty()) extensionDao.upsertAll(entities)

        val keep = entities.map { it.pkg }
        if (keep.isEmpty()) {
            extensionDao.deleteAllAvailable(repoId)
        } else {
            extensionDao.detachMissing(repoId, keep)
            extensionDao.deleteMissingAvailable(repoId, keep)
        }
        repoDao.markRefreshed(repoId, System.currentTimeMillis(), entities.size)
    }
}
