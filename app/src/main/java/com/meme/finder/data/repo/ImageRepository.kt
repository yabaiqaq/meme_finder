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

    /** FTS 搜索。 */
    fun search(query: String): Flow<List<ImageItem>> {
        val q = query.trim()
        if (q.isEmpty()) return observeAll()
        // 拼成 FTS4 前缀匹配：用户输入"哈哈" -> "哈哈*"
        val ftsQuery = q.split(Regex("\\s+"))
            .joinToString(" ") { "${escapeFts(it)}*" }
        return dao.search(ftsQuery).map { list -> list.map { it.toDomain() } }
    }

    /**
     * 增量扫描相册：
     * 1. 从 MediaStore 读出全部图片
     * 2. 新图 INSERT IGNORE（保留 OCR/labels/favorite 默认空值，交给后续 Worker）
     * 3. 已有图只刷新 media 元数据（uri/尺寸/文件名/分类），
     *    保留 ocr_text/labels/is_favorite/ocr_processed_at/label_processed_at
     *
     * 这样新增图片能进库、已处理过的 OCR 不会丢、收藏不会被清。
     */
    suspend fun rescan(): Int = withContext(Dispatchers.IO) {
        val scanned = mediaStoreSource.scanImages()
        if (scanned.isEmpty()) return@withContext 0

        val entities = scanned.map { ImageEntity.fromDomain(it) }
        val existingIds = dao.getAllIds().toSet()

        // 拆分：新行 vs 已有行
        val (newOnes, existingOnes) = entities.partition { it.id !in existingIds }

        // 新行直接插入（IGNORE 兜底，防并发）
        if (newOnes.isNotEmpty()) dao.insertNew(newOnes)

        // 已有行只刷 media 元数据，保留 OCR/labels/favorite
        for (e in existingOnes) {
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
        }
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

    private fun escapeFts(token: String): String {
        // FTS4 默认不会因中文报错；仅做最简单的清洗，避免特殊操作符
        return token.replace("\"", " ")
    }
}
