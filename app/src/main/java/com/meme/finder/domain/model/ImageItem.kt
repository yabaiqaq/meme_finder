package com.meme.finder.domain.model

/**
 * 单张图片的领域模型。UI 与数据库均使用本结构。
 */
data class ImageItem(
    val id: Long,                  // MediaStore 中的 _ID
    val uri: String,               // content://media/external/.../id
    val displayName: String,       // 文件名
    val path: String?,             // 物理路径（Android 10+ 可能不可用）
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val dateTakenMs: Long?,
    val bucketDisplayName: String?,  // 相册分组名
    val type: ImageType = ImageType.UNKNOWN,
    val ocrText: String? = null,      // OCR 提取到的全部文字（拼接）
    val isFavorite: Boolean = false,
    val ocrProcessedAt: Long = 0L,
)
