package com.meme.finder.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.meme.finder.domain.model.ImageItem
import com.meme.finder.domain.model.ImageType

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "path") val path: String?,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "date_added_sec") val dateAddedSec: Long,
    @ColumnInfo(name = "date_taken_ms") val dateTakenMs: Long?,
    @ColumnInfo(name = "bucket_display_name") val bucketDisplayName: String?,
    @ColumnInfo(name = "type") val type: ImageType,
    @ColumnInfo(name = "ocr_text") val ocrText: String?,
    // 已废弃的图片标签列：保留是为了不改动 Room schema（版本仍为 1），
    // 否则 fallbackToDestructiveMigration 会清掉用户已跑完的 OCR 数据。
    @ColumnInfo(name = "labels") val labels: List<String> = emptyList(),
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "ocr_processed_at") val ocrProcessedAt: Long = 0L,
    @ColumnInfo(name = "label_processed_at") val labelProcessedAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toDomain(): ImageItem = ImageItem(
        id = id,
        uri = uri,
        displayName = displayName,
        path = path,
        mimeType = mimeType,
        width = width,
        height = height,
        sizeBytes = sizeBytes,
        dateAddedSec = dateAddedSec,
        dateTakenMs = dateTakenMs,
        bucketDisplayName = bucketDisplayName,
        type = type,
        ocrText = ocrText,
        isFavorite = isFavorite,
        ocrProcessedAt = ocrProcessedAt,
    )

    companion object {
        fun fromDomain(item: ImageItem): ImageEntity = ImageEntity(
            id = item.id,
            uri = item.uri,
            displayName = item.displayName,
            path = item.path,
            mimeType = item.mimeType,
            width = item.width,
            height = item.height,
            sizeBytes = item.sizeBytes,
            dateAddedSec = item.dateAddedSec,
            dateTakenMs = item.dateTakenMs,
            bucketDisplayName = item.bucketDisplayName,
            type = item.type,
            ocrText = item.ocrText,
            isFavorite = item.isFavorite,
            ocrProcessedAt = item.ocrProcessedAt,
        )
    }
}
