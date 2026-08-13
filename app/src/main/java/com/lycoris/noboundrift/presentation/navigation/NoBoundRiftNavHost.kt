package com.lycoris.noboundrift.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lycoris.noboundrift.presentation.browse.BrowseScreen
import com.lycoris.noboundrift.presentation.recommendation.RecommendationScreen
import com.lycoris.noboundrift.presentation.detail.DetailScreen
import com.lycoris.noboundrift.presentation.library.LibraryScreen
import com.lycoris.noboundrift.presentation.reader.ReaderScreen
import com.lycoris.noboundrift.presentation.settings.SettingsAppearanceScreen
import com.lycoris.noboundrift.presentation.settings.SettingsBrowseScreen
import com.lycoris.noboundrift.presentation.settings.SettingsDownloadsScreen
import com.lycoris.noboundrift.presentation.settings.SettingsLibraryScreen
import com.lycoris.noboundrift.presentation.settings.SettingsNavigationScreen
import com.lycoris.noboundrift.presentation.settings.SettingsReaderScreen
import com.lycoris.noboundrift.presentation.settings.SettingsScreen

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: @Composable () -> Unit,
)

@Composable
fun NoBoundRiftNavHost(
    viewModel: NavHostViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showDiscover by viewModel.showDiscoverTab.collectAsStateWithLifecycle()

    // If the user is currently on the Discover screen and they toggle it off,
    // redirect them to Browse so they don't stay on a "hidden" destination.
    LaunchedEffect(showDiscover) {
        if (!showDiscover) {
            val onDiscover = currentDestination?.hierarchy?.any {
                it.route == Screen.Recommendation.route
            } == true
            if (onDiscover) {
                navController.navigate(Screen.Browse.createRoute()) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val allBottomNavItems = listOf(
        BottomNavItem(
            screen = Screen.Library,
            label = "Library",
            icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = "Library") },
        ),
        BottomNavItem(
            screen = Screen.Browse,
            label = "Browse",
            icon = { Icon(Icons.Default.Explore, contentDescription = "Browse") },
        ),
        BottomNavItem(
            screen = Screen.Recommendation,
            label = "Discover",
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Discover") },
        ),
        BottomNavItem(
            screen = Screen.Settings,
            label = "Settings",
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
        ),
    )
    val bottomNavItems = if (showDiscover) allBottomNavItems
    else allBottomNavItems.filter { it.screen != Screen.Recommendation }

    val showBottomBar = currentDestination?.hierarchy?.any { dest ->
        dest.route == Screen.Library.route ||
            dest.route == Screen.Browse.route ||
            dest.route == Screen.Recommendation.route ||
            dest.route == Screen.Settings.route
    } == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.screen.route
                            } == true,
                            onClick = {
                                val route = if (item.screen == Screen.Browse) Screen.Browse.createRoute() else item.screen.route
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = item.icon,
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    onMangaClick = { preview ->
                        navController.navigate(
                            Screen.Detail.createRoute(preview.sourceId, preview.url)
                        )
                    },
                    onChapterClick = { sourceId, mangaId, chapterUrl, mangaTitle ->
                        navController.navigate(
                            Screen.Reader.createRoute(sourceId, mangaId, chapterUrl, mangaTitle)
                        )
                    },
                )
            }

            composable(
                route = Screen.Browse.route,
                arguments = listOf(
                    navArgument(Screen.Browse.ARG_QUERY) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Screen.Browse.ARG_ALT_TITLES) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                BrowseScreen(
                    onMangaClick = { preview ->
                        navController.navigate(
                            Screen.Detail.createRoute(preview.sourceId, preview.url)
                        )
                    },
                )
            }

            composable(
                route = Screen.Detail.route,
                arguments = listOf(
                    navArgument(Screen.Detail.ARG_SOURCE_ID) { type = NavType.LongType },
                    navArgument(Screen.Detail.ARG_URL) { type = NavType.StringType },
                ),
            ) {
                DetailScreen(
                    onChapterClick = { sourceId, mangaId, chapterUrl, mangaTitle ->
                        navController.navigate(
                            Screen.Reader.createRoute(sourceId, mangaId, chapterUrl, mangaTitle)
                        )
                    },
                    onBackClick = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument(Screen.Reader.ARG_SOURCE_ID) { type = NavType.LongType },
                    navArgument(Screen.Reader.ARG_MANGA_ID) { type = NavType.StringType },
                    navArgument(Screen.Reader.ARG_CHAPTER_URL) { type = NavType.StringType },
                    navArgument(Screen.Reader.ARG_MANGA_TITLE) { type = NavType.StringType },
                ),
            ) {
                ReaderScreen(onBackClick = { navController.popBackStack() })
            }

            composable(Screen.Recommendation.route) {
                RecommendationScreen(
                    onMangaClick = { title, altTitles ->
                        navController.navigate(Screen.Browse.createRoute(title, altTitles)) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                            launchSingleTop = false
                        }
                    },
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAppearance = { navController.navigate(Screen.SettingsAppearance.route) },
                    onNavigateToBrowse = { navController.navigate(Screen.SettingsBrowse.route) },
                    onNavigateToReader = { navController.navigate(Screen.SettingsReader.route) },
                    onNavigateToLibrary = { navController.navigate(Screen.SettingsLibrary.route) },
                    onNavigateToDownloads = { navController.navigate(Screen.SettingsDownloads.route) },
                    onNavigateToNavigation = { navController.navigate(Screen.SettingsNavigation.route) },
                )
            }
            composable(Screen.SettingsAppearance.route) {
                SettingsAppearanceScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsBrowse.route) {
                SettingsBrowseScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsReader.route) {
                SettingsReaderScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsLibrary.route) {
                SettingsLibraryScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsDownloads.route) {
                SettingsDownloadsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsNavigation.route) {
                SettingsNavigationScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
