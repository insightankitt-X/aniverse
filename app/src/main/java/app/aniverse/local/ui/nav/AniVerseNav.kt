package app.aniverse.local.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.aniverse.local.domain.model.Series
import app.aniverse.local.domain.model.SourceRef
import app.aniverse.local.domain.repository.DownloadRepository
import app.aniverse.local.domain.repository.ExtensionRepository
import app.aniverse.local.domain.repository.HistoryRepository
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.ui.components.AniBottomBar
import app.aniverse.local.ui.components.AniTab
import app.aniverse.local.ui.components.AniTabs
import app.aniverse.local.ui.screens.browse.BrowseScreen
import app.aniverse.local.ui.screens.browse.SourceFeedScreen
import app.aniverse.local.ui.screens.details.DetailsScreen
import app.aniverse.local.ui.screens.downloads.DownloadsScreen
import app.aniverse.local.ui.screens.extensions.ExtensionsScreen
import app.aniverse.local.ui.screens.history.HistoryScreen
import app.aniverse.local.ui.screens.home.HomeScreen
import app.aniverse.local.ui.screens.library.LibraryScreen
import app.aniverse.local.ui.screens.more.MoreScreen
import app.aniverse.local.ui.screens.player.PlayerScreen
import app.aniverse.local.ui.screens.repositories.RepositoriesScreen
import app.aniverse.local.ui.screens.search.SearchScreen
import app.aniverse.local.ui.screens.settings.AppearanceScreen
import app.aniverse.local.ui.screens.settings.SettingsScreen
import app.aniverse.local.ui.screens.updates.UpdatesScreen
import app.aniverse.local.ui.theme.LocalReduceMotion
import app.aniverse.local.ui.theme.Motion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

object Routes {
    const val HOME = "home"
    const val BROWSE = "browse"
    const val LIBRARY = "library"
    const val UPDATES = "schedule"
    const val MORE = "profile"
    const val SEARCH = "search"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
    const val EXTENSIONS = "extensions"
    const val REPOSITORIES = "repositories"
    const val SOURCE_FEED = "source/{pkg}/{sourceId}/{name}"
    const val DETAILS = "details?seriesId={seriesId}&pkg={pkg}&sourceId={sourceId}&remoteId={remoteId}&title={title}"
    const val PLAYER = "player/{episodeId}"

    fun sourceFeed(ref: SourceRef): String =
        "source/${Uri.encode(ref.pkg)}/${ref.sourceId}/${Uri.encode(ref.name)}"

    fun details(series: Series): String = buildString {
        append("details?seriesId=").append(series.id)
        append("&pkg=").append(Uri.encode(series.sourcePkg))
        append("&sourceId=").append(series.sourceId)
        append("&remoteId=").append(Uri.encode(series.remoteId))
        append("&title=").append(Uri.encode(series.title))
    }

    fun details(seriesId: Long): String = "details?seriesId=$seriesId&pkg=&sourceId=0&remoteId=&title="

    fun player(episodeId: Long): String = "player/$episodeId"
}

@HiltViewModel
class NavBadgesViewModel @Inject constructor(
    extensionRepository: ExtensionRepository,
    libraryRepository: LibraryRepository,
    downloadRepository: DownloadRepository,
    historyRepository: HistoryRepository,
) : ViewModel() {

    data class Badges(
        val extensionUpdates: Int = 0,
        val newEpisodes: Int = 0,
        val downloads: Int = 0,
    )

    private val _badges = MutableStateFlow(Badges())
    val badges = _badges.asStateFlow()

    init {
        viewModelScope.launch {
            extensionRepository.observeUpdateCount().collect { count ->
                _badges.update { it.copy(extensionUpdates = count) }
            }
        }
        viewModelScope.launch {
            libraryRepository.observeUpdateCount().collect { count ->
                _badges.update { it.copy(newEpisodes = count) }
            }
        }
        viewModelScope.launch {
            downloadRepository.observeDownloads().collect { groups ->
                _badges.update { it.copy(downloads = groups.sumOf { it.items.size }) }
            }
        }
        viewModelScope.launch {
            historyRepository.observeNewEpisodes().collect { items ->
                _badges.update { it.copy(newEpisodes = maxOf(it.newEpisodes, items.size)) }
            }
        }
    }
}

@Composable
fun AniVerseNav(
    navController: NavHostController = rememberNavController(),
    badgesViewModel: NavBadgesViewModel = hiltViewModel(),
) {
    val badges by badgesViewModel.badges.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val tabs = remember(badges) {
        AniTabs.mapIndexed { index, tab ->
            when (index) {
                1 -> tab.copy(badge = badges.newEpisodes)
                3 -> tab.copy(badge = badges.extensionUpdates + badges.downloads)
                else -> tab
            }
        }
    }
    val showBar = tabs.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                AniBottomBar(
                    tabs = tabs,
                    currentRoute = currentRoute,
                    onSelect = { tab -> navigateTab(navController, currentRoute, tab) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = {
                when {
                    reduceMotion -> EnterTransition.None
                    isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route) ->
                        fadeIn(tween(240)) + scaleIn(
                            initialScale = 0.985f,
                            animationSpec = tween(280, easing = Motion.Emphasized),
                        )

                    else -> fadeIn(tween(200)) + slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(320, easing = Motion.Emphasized),
                        initialOffset = { it / 8 },
                    )
                }
            },
            exitTransition = {
                if (reduceMotion) {
                    ExitTransition.None
                } else {
                    fadeOut(tween(150)) + scaleOut(targetScale = 1.008f, animationSpec = tween(180))
                }
            },
            popEnterTransition = {
                if (reduceMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(200)) + slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = Motion.Emphasized),
                        initialOffset = { it / 8 },
                    )
                }
            },
            popExitTransition = {
                if (reduceMotion) ExitTransition.None else fadeOut(tween(150))
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSeries = { series -> navController.navigate(Routes.details(series)) },
                    onOpenSeriesById = { id -> navController.navigate(Routes.details(id)) },
                    onOpenSource = { ref -> navController.navigate(Routes.sourceFeed(ref)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenUpdates = { navigateTab(navController, currentRoute, tabs[1]) },
                    onOpenLibrary = { navigateTab(navController, currentRoute, tabs[2]) },
                    onOpenExtensions = { navController.navigate(Routes.EXTENSIONS) },
                    onPlay = { episodeId -> navController.navigate(Routes.player(episodeId)) },
                )
            }
            composable(Routes.UPDATES) {
                UpdatesScreen(onOpenSeries = { seriesId -> navController.navigate(Routes.details(seriesId)) })
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenDetails = { series -> navController.navigate(Routes.details(series)) },
                    onBrowse = { navController.navigate(Routes.BROWSE) { launchSingleTop = true } },
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                    onOpenExtensions = { navController.navigate(Routes.EXTENSIONS) },
                    onOpenRepositories = { navController.navigate(Routes.REPOSITORIES) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
                    onOpenBrowse = { navController.navigate(Routes.BROWSE) { launchSingleTop = true } },
                    extensionUpdates = badges.extensionUpdates,
                    downloadCount = badges.downloads,
                )
            }
            composable(Routes.BROWSE) {
                BrowseScreen(
                    onOpenSource = { ref -> navController.navigate(Routes.sourceFeed(ref)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenExtensions = { navController.navigate(Routes.EXTENSIONS) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenSeries = { series -> navController.navigate(Routes.details(series)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.DOWNLOADS) { DownloadsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExtensions = { navController.navigate(Routes.EXTENSIONS) },
                    onOpenRepositories = { navController.navigate(Routes.REPOSITORIES) },
                    onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
                )
            }
            composable(Routes.APPEARANCE) {
                AppearanceScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EXTENSIONS) {
                ExtensionsScreen(
                    onOpenRepositories = { navController.navigate(Routes.REPOSITORIES) },
                    onOpenSource = { ref -> navController.navigate(Routes.sourceFeed(ref)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.REPOSITORIES) {
                RepositoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.SOURCE_FEED,
                arguments = listOf(
                    navArgument("pkg") { type = NavType.StringType },
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                ),
            ) {
                SourceFeedScreen(
                    onOpenSeries = { series -> navController.navigate(Routes.details(series)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.DETAILS,
                arguments = listOf(
                    navArgument("seriesId") { type = NavType.StringType; defaultValue = "0" },
                    navArgument("pkg") { type = NavType.StringType; defaultValue = "" },
                    navArgument("sourceId") { type = NavType.StringType; defaultValue = "0" },
                    navArgument("remoteId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                DetailsScreen(
                    onPlay = { episodeId -> navController.navigate(Routes.player(episodeId)) },
                    onOpenRelated = { related -> navController.navigate(Routes.details(related)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("episodeId") { type = NavType.LongType }),
            ) { entry ->
                val episodeId = entry.arguments?.getLong("episodeId") ?: 0L
                PlayerScreen(
                    episodeId = episodeId,
                    onBack = { navController.popBackStack() },
                    onPlayEpisode = { next ->
                        navController.navigate(Routes.player(next)) {
                            popUpTo(Routes.PLAYER.replace("{episodeId}", episodeId.toString())) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

private fun navigateTab(navController: NavHostController, currentRoute: String?, tab: AniTab) {
    if (currentRoute == tab.route) return
    navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun isTabRoute(route: String?): Boolean = AniTabs.any { it.route == route }
