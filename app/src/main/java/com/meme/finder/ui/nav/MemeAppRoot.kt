package com.meme.finder.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meme.finder.R
import com.meme.finder.ui.screen.detail.DetailScreen
import com.meme.finder.ui.screen.favorites.FavoritesScreen
import com.meme.finder.ui.screen.gallery.GalleryScreen
import com.meme.finder.ui.screen.search.SearchScreen
import com.meme.finder.ui.screen.settings.SettingsScreen

@Composable
fun MemeAppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 详情页时隐藏顶部/底部栏
    val showBars = currentRoute in topLevelRoutes

    Scaffold(
        topBar = { if (showBars) MemeTopBar(currentRoute) else {} },
        bottomBar = { if (showBars) MemeBottomBar(navController, currentRoute) else {} },
    ) { padding ->
        MemeNavHost(navController, padding)
    }
}

private val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

@Composable
private fun MemeTopBar(currentRoute: String?) {
    val dest = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
    val titleRes = dest?.labelRes ?: R.string.app_name
    TopAppBar(title = { Text(stringResource(titleRes)) })
}

@Composable
private fun MemeBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(stringResource(dest.labelRes)) },
            )
        }
    }
}

@Composable
fun MemeNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.GALLERY,
        modifier = Modifier.padding(contentPadding),
    ) {
        composable(Routes.GALLERY) {
            GalleryScreen(onOpenImage = { id -> navController.navigate(Routes.detail(id)) })
        }
        composable(Routes.SEARCH) {
            SearchScreen(onOpenImage = { id -> navController.navigate(Routes.detail(id)) })
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(onOpenImage = { id -> navController.navigate(Routes.detail(id)) })
        }
        composable(Routes.SETTINGS) { SettingsScreen() }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("imageId") { type = NavType.StringType }),
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
