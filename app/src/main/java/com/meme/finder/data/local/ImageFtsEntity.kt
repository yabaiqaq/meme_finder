package com.meme.finder.data.local

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 全文搜索表，content 表指向 [ImageEntity]。
 * 索引文本 = OCR 文字 + 文件名 + 标签（以空格拼接）。
 */
@Entity(tableName = "images_fts")
@Fts4(contentEntity = ImageEntity::class)
data class ImageFtsEntity(
    val content: String,
)
