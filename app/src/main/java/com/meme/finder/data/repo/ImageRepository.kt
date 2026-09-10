package com.meme.finder.data.repo

import com.meme.finder.data.local.ImageDao
import com.meme.finder.data.local.ImageEntity
import com.meme.finder.data.media.MediaStoreSource
import com.meme.finder.domain.model.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用级图片仓库：MediaStore 扫描 -> Room 持久化 -> UI 订阅。
 */
@Singleton
class ImageRepository @Inject constructor(
    private val mediaStoreSource: MediaStoreSource,
    private val dao: ImageDao,
) {

    /** 订阅所有图片（Room Flow）。 */
    fun observeAll(): Flow<List<ImageItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** 订阅收藏。 */
    fun observeFavorites(): Flow<List<ImageItem>> =
        dao.observeFavorites().map { list -> list.map { it.toDomain() } }

    /**
     * 搜索：关键词命中 OCR 文本 / 文件名 / 标签（子串匹配）。
     * 不再用 FTS 前缀匹配 —— 中文连续字会被 FTS 分词器整成一个 token，
     * 搜中间的字（如"豆包"匹配"变成豆包了"）前缀对不上会漏。
     * LIKE '%关键词%' 能命中任意位置子串，语义正确。
     */
    fun search(query: String): Flow<List<ImageItem>> {
        val q = query.trim()
        if (q.isEmpty()) return observeAll()
        return dao.search(q).map { list -> list.map { it.toDomain() } }
    }

    /**
     * 增量扫描相册：
     * 1. 从 MediaStore 读出全部图片
     * 2. 新图 INSERT IGNORE（保留 OCR/labels/favorite 默认空值，交给后续 Worker）
     * 3. 已有图只刷新 media 元数据（uri/尺寸/文件名/分类），
     *    保留 ocr_text/labels/is_favorite/ocr_processed_at/label_processed_at
     *
     * [onProgress] 用于实时推送进度 (done, total)，UI 据此显示进度条。
     */
    suspend fun rescan(
        onProgress: (suspend (done: Int, total: Int) -> Unit)? = null,
    ): Int = withContext(Dispatchers.IO) {
        val scanned = mediaStoreSource.scanImages()
        if (scanned.isEmpty()) return@withContext 0
        val total = scanned.size
        var done = 0

        val entities = scanned.map { ImageEntity.fromDomain(it) }
        val existingIds = dao.getAllIds().toSet()

        // 拆分：新行 vs 已有行
        val (newOnes, existingOnes) = entities.partition { it.id !in existingIds }

        // 新行直接插入（IGNORE 兜底，防并发）
        if (newOnes.isNotEmpty()) dao.insertNew(newOnes)
        done += newOnes.size
        onProgress?.invoke(done, total)

        // 已有行只刷 media 元数据，保留 OCR/labels/favorite
        // 每 50 条刷一次进度，避免过度回调
        for ((idx, e) in existingOnes.withIndex()) {
            dao.updateMediaMeta(
                id = e.id,
                uri = e.uri,
                displayName = e.displayName,
                path = e.path,
                mimeType = e.mimeType,
                width = e.width,
                height = e.height,
                sizeBytes = e.sizeBytes,
                dateAddedSec = e.dateAddedSec,
                dateTakenMs = e.dateTakenMs,
                bucketDisplayName = e.bucketDisplayName,
                type = e.type.name,
            )
            done++
            if (idx % 50 == 0) onProgress?.invoke(done, total)
        }
        onProgress?.invoke(done, total)
        scanned.size
    }

    suspend fun getById(id: Long): ImageItem? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    /** 返回 OCR 待处理列表。 */
    suspend fun getOcrPending(): List<ImageItem> = withContext(Dispatchers.IO) {
        dao.getOcrPending().map { it.toDomain() }
    }

    /** 标签待处理列表。 */
    suspend fun getLabelPending(): List<ImageItem> = withContext(Dispatchers.IO) {
        dao.getLabelPending().map { it.toDomain() }
    }

    /** 写回 OCR 结果。 */
    suspend fun setOcrResult(id: Long, text: String) {
        dao.setOcrResult(id, text, System.currentTimeMillis())
    }

    /** 写回标签结果。 */
    suspend fun setLabelResult(id: Long, labels: List<String>) {
        dao.setLabelResult(id, labels, System.currentTimeMillis())
    }

    /** 切换收藏。 */
    suspend fun setFavorite(id: Long, favorite: Boolean) {
        dao.setFavorite(id, favorite)
    }

    /** 标签识别后更新分类。 */
    suspend fun updateType(id: Long, type: com.meme.finder.domain.model.ImageType) {
        dao.updateType(id, type.name)
    }
}
