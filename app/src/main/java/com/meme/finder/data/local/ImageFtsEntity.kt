package com.meme.finder.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 全文搜索表，content 表指向 [ImageEntity]。
 * FTS 列名必须与 [ImageEntity] 中存在的列名一一对应；
 * 这里索引文件名和 OCR 文本（标签是 List 不适合直接进 FTS）。
 */
@Entity(tableName = "images_fts")
@Fts4(contentEntity = ImageEntity::class)
data class ImageFtsEntity(
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "ocr_text") val ocrText: String,
)
