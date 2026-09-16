package com.meme.finder.ui.screen.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meme.finder.R
import com.meme.finder.ui.component.FastScroller
import com.meme.finder.ui.component.ImageGridCell
import com.meme.finder.ui.component.ProgressPanel
import com.meme.finder.util.PermissionUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    vm: GalleryViewModel = hiltViewModel(),
    onOpenImage: (Long) -> Unit = {},
) {
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

    Column(Modifier.fillMaxSize()) {
        ProgressPanel(state.progress)

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
                else -> GroupedGallery(
                    groups = state.groups,
                    onOpenImage = onOpenImage,
                )
            }
            state.error?.let { msg ->
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) { Text(msg, color = MaterialTheme.colorScheme.error) }
            }
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
        Icon(
            Icons.Filled.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_gallery), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onScan) { Text(stringResource(R.string.action_scan)) }
    }
}

/**
 * 按月分组的相册网格：
 * - 用 forEach 遍历 [GalleryGroup] 列表，每个组先插一个 Header（span = maxLineSpan 占满整行），
 *   再插该组的 items（默认单列 span）
 * - 父 Box 叠加 FastScroller，提供快速拖动 + 当前月份浮层
 */
@Composable
private fun GroupedGallery(
    groups: List<GalleryGroup>,
    onOpenImage: (Long) -> Unit,
) {
    val gridState = rememberLazyGridState()
    // 把分组扁平成 entry 序列，供 FastScroller 的 totalItems 和 findVisibleHeader 用
    val flatEntries = remember(groups) { flattenGroups(groups) }
    // 当前可见月份（用于 FastScroller 浮层显示）
    val currentHeader by remember(flatEntries) {
        derivedStateOf {
            findVisibleHeader(flatEntries, gridState.firstVisibleItemIndex)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            groups.forEach { group ->
                // 月分头占满整行
                item(
                    key = "header_${group.header.key}",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "header",
                ) {
                    MonthHeader(group.header)
                }
                // 月内图片：默认单列 span
                items(
                    items = group.items,
                    key = { it.id },
                    contentType = { "image" },
                ) { item ->
                    ImageGridCell(item = item, onClick = onOpenImage)
                }
            }
        }

        FastScroller(
            state = gridState,
            totalItems = flatEntries.size,
            currentLabel = { currentHeader?.text },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MonthHeader(header: GalleryEntry.Header) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 左侧短粗线作"分隔条"
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            header.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
