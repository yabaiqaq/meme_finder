package com.meme.finder.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.meme.finder.domain.model.ImageItem
import java.io.File
import java.io.FileOutputStream

/**
 * 把表情包/图片通过系统 Intent 分享出去。
 *
 * 实现：把 content://media/external/.../id 流复制到 cacheDir 下的临时文件，
 * 用应用自己的 FileProvider 暴露，保证接收方有读权限。
 */
object ShareUtil {

    fun share(context: Context, item: ImageItem) {
        runCatching {
            val sourceUri = Uri.parse(item.uri)
            val ext = extForMime(item.mimeType)
            val tempFile = File(context.cacheDir, "shared_${item.id}.$ext")
            context.contentResolver.openInputStream(sourceUri).use { input ->
                FileOutputStream(tempFile).use { out -> input?.copyTo(out) }
            }
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile,
            )
            val mime = item.mimeType.ifBlank { "image/*" }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_TITLE, item.displayName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享到…"))
        }
    }

    private fun extForMime(mime: String): String = when {
        mime.endsWith("png", true) -> "png"
        mime.endsWith("gif", true) -> "gif"
        mime.endsWith("webp", true) -> "webp"
        mime.endsWith("bmp", true) -> "bmp"
        else -> "jpg"
    }
}
