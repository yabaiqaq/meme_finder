package com.meme.finder.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.meme.finder.R

/** 顶层 Tab 目的地。 */
enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Gallery("gallery", R.string.tab_gallery, Icons.Outlined.Collections),
    Search("search", R.string.tab_search, Icons.Outlined.Search),
    Favorites("favorites", R.string.tab_favorites, Icons.Outlined.FavoriteBorder),
    Settings("settings", R.string.tab_settings, Icons.Outlined.Settings),
}

object Routes {
    const val GALLERY = "gallery"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{imageId}"
    fun detail(imageId: Long) = "detail/$imageId"
}
