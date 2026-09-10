package com.meme.finder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {

    /** 全量替换写入（仅用于首次建库/重置，会覆盖 OCR/labels/favorite）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ImageEntity>)

    /** 仅插入新行，已存在的 id 不动 —— 保留已有 OCR/labels/favorite。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(items: List<ImageEntity>): List<Long>

    @Update
    suspend fun update(item: ImageEntity)

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM images ORDER BY date_added_sec DESC")
    fun observeAll(): Flow<List<ImageEntity>>

    @Query("SELECT id FROM images")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getById(id: Long): ImageEntity?

    @Query("SELECT * FROM images WHERE is_favorite = 1 ORDER BY date_added_sec DESC")
    fun observeFavorites(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE ocr_processed_at = 0 ORDER BY date_added_sec DESC")
    suspend fun getOcrPending(): List<ImageEntity>

    @Query("SELECT * FROM images WHERE label_processed_at = 0 ORDER BY date_added_sec DESC")
    suspend fun getLabelPending(): List<ImageEntity>

    @Query("UPDATE images SET ocr_text = :text, ocr_processed_at = :timestamp, updated_at = :timestamp WHERE id = :id")
    suspend fun setOcrResult(id: Long, text: String, timestamp: Long)

    @Query("UPDATE images SET labels = :labels, label_processed_at = :timestamp, updated_at = :timestamp WHERE id = :id")
    suspend fun setLabelResult(id: Long, labels: List<String>, timestamp: Long)

    @Query("UPDATE images SET is_favorite = :favorite, updated_at = :timestamp WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE images SET type = :type, updated_at = :timestamp WHERE id = :id")
    suspend fun updateType(id: Long, type: String, timestamp: Long = System.currentTimeMillis())

    /**
     * 增量更新 media 元数据（uri/尺寸/文件名/分类等），保留 OCR/labels/favorite/处理时间戳。
     * 仅对已存在的行调用。
     */
    @Query("""
        UPDATE images SET
            uri = :uri,
            display_name = :displayName,
            path = :path,
            mime_type = :mimeType,
            width = :width,
            height = :height,
            size_bytes = :sizeBytes,
            date_added_sec = :dateAddedSec,
            date_taken_ms = :dateTakenMs,
            bucket_display_name = :bucketDisplayName,
            type = :type,
            updated_at = :timestamp
        WHERE id = :id
    """)
    suspend fun updateMediaMeta(
        id: Long,
        uri: String,
        displayName: String,
        path: String?,
        mimeType: String,
        width: Int,
        height: Int,
        sizeBytes: Long,
        dateAddedSec: Long,
        dateTakenMs: Long?,
        bucketDisplayName: String?,
        type: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    /**
     * 搜索：关键词命中 OCR 文本 / 文件名 / 标签（子串匹配）。
     * 用 LIKE '%关键词%' —— 中文场景下 FTS 前缀匹配会把"变成豆包了"整成
     * 一个 token，搜"豆包"前缀不匹配就搜不到；LIKE 子串能命中任意位置。
     */
    @Query("""
        SELECT * FROM images
        WHERE ocr_text LIKE '%' || :query || '%'
           OR display_name LIKE '%' || :query || '%'
           OR labels LIKE '%' || :query || '%'
        ORDER BY date_added_sec DESC
    """)
    fun search(query: String): Flow<List<ImageEntity>>
}
