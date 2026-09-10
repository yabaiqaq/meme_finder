package com.meme.finder.data.repo

import com.meme.finder.data.local.ImageDao
import com.meme.finder.data.local.ImageEntity
import com.meme.finder.data.media.MediaStoreSource
import com.meme.finder.domain.model.ImageItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
     * 扫描相册：从 MediaStore 读出全部图片并 upsert 到 Room。
     * 已存在的项目 OCR/标签/收藏状态保持不变。
     */
    suspend fun rescan(): Int {
        val scanned = mediaStoreSource.scanImages()
        val existing = scanned.map { ImageEntity.fromDomain(it) }
        dao.upsertAll(existing)
        return scanned.size
    }

    suspend fun getById(id: Long): ImageItem? = dao.getById(id)?.toDomain()

    /** 返回 OCR 待处理列表。 */
    suspend fun getOcrPending(): List<ImageItem> =
        dao.getOcrPending().map { it.toDomain() }

    /** 标签待处理列表（v0.4 用）。 */
    suspend fun getLabelPending(): List<ImageItem> =
        dao.getLabelPending().map { it.toDomain() }

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
