package com.meme.finder.data.repo

import com.meme.finder.data.media.MediaStoreSource
import com.meme.finder.domain.model.ImageItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用级别的图片仓库。负责把设备原始数据合并到本地索引（v0.2 起加入 Room 持久化）。
 */
@Singleton
class ImageRepository @Inject constructor(
    private val mediaStoreSource: MediaStoreSource,
) {

    /** 扫描相册（不持久化）。v0.2 起改为先读 Room，缺失再扫描并落库。 */
    suspend fun scanAll(limit: Int = Int.MAX_VALUE): List<ImageItem> =
        mediaStoreSource.scanImages(limit)

    /** 只读取本地已有的图片（数据库命中），用于搜索/列表展示。 */
    suspend fun getAll(): List<ImageItem> = scanAll()
}
