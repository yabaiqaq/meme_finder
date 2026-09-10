package com.meme.finder

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MemeApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    /**
     * 自定义 Coil ImageLoader，优化 1.8 万图片场景下的加载性能：
     * - 内存缓存占可用内存 30%，可见 cell 命中即出图
     * - 磁盘缓存 500MB，滚动反复回到的图片不重新解码
     * - crossfade 过渡更平滑
     * Coil 默认会按 AsyncImage 的目标 size downsample，不再解码原图
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.30)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .maxSizeBytes(500L * 1024 * 1024)
                .build()
        }
        .build()
}
