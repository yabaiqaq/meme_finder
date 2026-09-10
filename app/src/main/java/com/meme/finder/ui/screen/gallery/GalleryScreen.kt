package com.meme.finder.ui.screen.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.meme.finder.R
import com.meme.finder.domain.model.ImageItem
import com.meme.finder.util.PermissionUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(vm: GalleryViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasPermission = PermissionUtil.hasImageReadPermission(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.rescan()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && !state.hasScanned && !state.isLoading) vm.rescan()
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { vm.rescan() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            !hasPermission -> {
                PermissionPrompt(onRequest = {
                    permissionLauncher.launch(PermissionUtil.imageReadPermission)
                })
            }
            state.isLoading && state.images.isEmpty() -> LoadingState()
            state.images.isEmpty() -> EmptyGallery(onScan = { vm.rescan() })
            else -> ImageGrid(state.images)
        }
        state.error?.let { msg ->
            Box(
                Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) { Text(msg, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.permission_rationale), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onRequest) { Text(stringResource(R.string.permission_grant)) }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.action_scan))
        }
    }
}

@Composable
private fun EmptyGallery(onScan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_gallery), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onScan) { Text(stringResource(R.string.action_scan)) }
    }
}

@Composable
private fun ImageGrid(items: List<ImageItem>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { item -> ImageCell(item) }
    }
}

@Composable
private fun ImageCell(item: ImageItem) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
