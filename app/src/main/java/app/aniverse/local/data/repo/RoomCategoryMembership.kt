package app.aniverse.local.data.repo

import app.aniverse.local.data.db.CategoryDao
import app.aniverse.local.domain.repository.CategoryMembership
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCategoryMembership @Inject constructor(
    private val categoryDao: CategoryDao,
) : CategoryMembership {
    override fun observeMembers(): Flow<Map<Long, Set<Long>>> = categoryDao.observeLinks().map { links ->
        links.groupBy { it.categoryId }.mapValues { (_, refs) -> refs.map { it.seriesId }.toSet() }
    }
}
