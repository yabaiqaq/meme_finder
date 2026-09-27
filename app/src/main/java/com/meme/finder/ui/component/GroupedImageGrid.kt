package com.meme.finder.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 按月分组的图片网格 + 右侧快速滚动条，相册页与搜索页共用。
 *
 * 每个组先插一个 Header（span = maxLineSpan 占满整行），再插该组的图片 cell。
 * Header 与图片共用一个扁平 index 序列（[flattenGroups]），FastScroller 据此
 * 把滚动条位置换算成目标 index，并显示当前月份浮层。
 */
@Composable
fun GroupedImageGrid(
    groups: List<MonthGroup>,
    onOpenImage: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val flatEntries = remember(groups) { flattenGroups(groups) }
    val currentHeader by remember(flatEntries) {
        derivedStateOf { findVisibleHeader(flatEntries, gridState.firstVisibleItemIndex) }
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            groups.forEach { group ->
                item(
                    key = "header_${group.header.key}",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "header",
                ) {
                    MonthHeader(group.header)
                }
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
private fun MonthHeader(header: MonthEntry.Header) {
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
