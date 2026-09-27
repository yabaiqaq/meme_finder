package com.meme.finder.ui.component

import android.graphics.drawable.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 按 URI 加载图片，动图（GIF / 动态 WebP）自动播放，离开组合时自动停帧。
 *
 * Coil 解码出的动图 drawable 分两种：API 28+ 是 ImageDecoder.AnimatedImageDrawable，
 * 26/27 走 coil-gif 的 MovieDrawable。两者都实现平台的 Animatable，故统一按它处理。
 *
 * 网格缩略图故意不用本组件，让动图只渲染首帧 —— 1.8 万张图的列表里同时跑几十个
 * GIF 动画会直接拖垮滚动。
 */
@Composable
fun UriImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    var animatable by remember(uri) { mutableStateOf<Animatable?>(null) }

    val request = remember(uri, context) {
        ImageRequest.Builder(context)
            .data(uri)
            .listener(onSuccess = { _, result ->
                animatable = result.drawable as? Animatable
            })
            .build()
    }

    LaunchedEffect(animatable) { animatable?.start() }
    DisposableEffect(animatable) {
        val current = animatable
        onDispose { current?.stop() }
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
    )
}
