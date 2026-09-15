package com.meme.finder.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemeAppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 详情页时隐藏顶部/底部栏
    val showBars = currentRoute in topLevelRoutes

    // 顶栏滚动联动：滚动内容时 LargeTopAppBar 收缩为小标题
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { if (showBars) MemeTopBar(currentRoute, scrollBehavior) else {} },
        bottomBar = { if (showBars) MemeBottomBar(navController, currentRoute) else {} },
    ) { padding ->
        MemeNavHost(navController, padding)
    }
}

private val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemeTopBar(
    currentRoute: String?,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val dest = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
    val titleRes = dest?.labelRes ?: R.string.app_name
    LargeTopAppBar(
        title = {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun MemeBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
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
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
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
