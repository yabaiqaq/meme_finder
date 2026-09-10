package com.meme.finder.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.meme.finder.domain.model.ImageItem
import com.meme.finder.domain.model.ImageType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 直接读取 MediaStore.Images，把图片行变成 [ImageItem]。
 * - Android 10+ 自动按 bucket 显示相册分组
 * - 默认只读图片，不包含视频
 */
@Singleton
class MediaStoreSource @Inject constructor(
    private val resolver: ContentResolver,
    private val typeClassifier: ImageTypeClassifier,
) {

    /** 扫描相册，返回所有图片。最新的在前。 */
    fun scanImages(limit: Int = Int.MAX_VALUE): List<ImageItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC" +
            (if (limit == Int.MAX_VALUE) "" else " LIMIT $limit")

        val out = ArrayList<ImageItem>()
        resolver.query(collection, projection, null, null, sortOrder)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
            val mimeIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateAddedIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateTakenIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val bucketIdx = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val path = dataIdx.takeIf { it >= 0 }?.let { c.getString(it) }
                val mime = c.getString(mimeIdx) ?: "image/*"
                val width = c.getInt(widthIdx).takeIf { it > 0 } ?: 0
                val height = c.getInt(heightIdx).takeIf { it > 0 } ?: 0
                val size = c.getLong(sizeIdx)
                val dateAdded = c.getLong(dateAddedIdx)
                val dateTaken = dateTakenIdx.takeIf { it >= 0 }?.let { c.getLong(it) }
                val bucket = bucketIdx.takeIf { it >= 0 }?.let { c.getString(it) }
                val name = c.getString(nameIdx) ?: "image-$id"

                val contentUri = ContentUris.withAppendedId(collection, id)
                val type = typeClassifier.classify(
                    width = width,
                    height = height,
                    sizeBytes = size,
                    mime = mime,
                    displayName = name,
                    path = path,
                )

                out += ImageItem(
                    id = id,
                    uri = contentUri.toString(),
                    displayName = name,
                    path = path,
                    mimeType = mime,
                    width = width,
                    height = height,
                    sizeBytes = size,
                    dateAddedSec = dateAdded,
                    dateTakenMs = dateTaken,
                    bucketDisplayName = bucket,
                    type = type,
                )
            }
        }
        return out
    }

    /** 仅查询指定 uri 列表（增量更新）。 */
    fun scanUris(uris: List<Uri>): List<ImageItem> {
        val ids = uris.mapNotNull { it.lastPathSegment?.toLongOrNull() }.toSet()
        if (ids.isEmpty()) return emptyList()
        return scanImages().filter { it.id in ids }
    }
}

/**
 * 基于尺寸/比例/文件名的启发式分类器。
 * 真正"表情包"难以绝对区分，这里给一个合理的初判，后续用 OCR + 标签细化。
 */
@Singleton
class ImageTypeClassifier @Inject constructor() {

    fun classify(
        width: Int,
        height: Int,
        sizeBytes: Long,
        mime: String,
        displayName: String,
        path: String?,
    ): ImageType {
        val name = displayName.lowercase()
        when {
            name.contains("screenshot", true) || name.contains("截图") ||
                name.contains("screen_", true) || name.startsWith("img_screenshot") ->
                return ImageType.SCREENSHOT
            mime.endsWith("gif", true) -> return ImageType.STICKER
            mime.endsWith("webp", true) && sizeBytes < 800 * 1024 -> return ImageType.MEME
        }

        if (width <= 0 || height <= 0) return ImageType.UNKNOWN

        val ratio = width.toFloat() / height.toFloat()
        val maxDim = maxOf(width, height)
        val minDim = minOf(width, height)
        val isSquare = ratio in 0.85f..1.18f
        val isSmall = maxDim in 200..1500

        return when {
            isSquare && isSmall && minDim >= 200 && sizeBytes < 1_500 * 1024 ->
                ImageType.MEME
            ratio in 0.45f..0.62f && maxDim > 1000 ->
                ImageType.SCREENSHOT
            else -> ImageType.PHOTO
        }
    }
}
