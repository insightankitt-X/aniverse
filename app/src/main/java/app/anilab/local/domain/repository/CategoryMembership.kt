package app.anilab.local.domain.repository

import kotlinx.coroutines.flow.Flow

/** Which library series belong to which category, for filtering the library grid. */
interface CategoryMembership {
    fun observeMembers(): Flow<Map<Long, Set<Long>>>
}
