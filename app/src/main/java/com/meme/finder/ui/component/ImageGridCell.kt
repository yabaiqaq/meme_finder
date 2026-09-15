package com.meme.finder.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.meme.finder.domain.model.ImageItem

/**
 * 网格图片 cell：圆角 12dp、按压缩放反馈、surfaceVariant 占位底色。
 * Gallery/Search/Favorites 共用，保证视觉节奏一致。
 */
@Composable
fun ImageGridCell(
    item: ImageItem,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 显式 size=240，Coil 据此 downsample 到 cell 显示所需像素，不解码原图
    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(240)
            .crossfade(true)
            .build()
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableWithPress { onClick(item.id) },
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
