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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ImageEntity>)

    @Update
    suspend fun update(item: ImageEntity)

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM images ORDER BY date_added_sec DESC")
    fun observeAll(): Flow<List<ImageEntity>>

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
     * FTS 搜索：关键词命中 OCR 文本/文件名/标签。
     * 用 * 前缀匹配，输入"哈哈"能搜到"哈哈哈"。
     */
    @Query("""
        SELECT i.* FROM images i
        JOIN images_fts f ON f.rowid = i.id
        WHERE images_fts MATCH :query
        ORDER BY i.date_added_sec DESC
    """)
    fun search(query: String): Flow<List<ImageEntity>>

    @Transaction
    suspend fun upsertAndRebuildFts(items: List<ImageEntity>) {
        upsertAll(items)
        // Room FTS contentEntity 自动维护索引；插入即生效
    }
}
