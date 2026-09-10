package com.meme.finder.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.meme.finder.domain.model.ImageItem

/**
 * 用系统其他应用打开图片（ACTION_VIEW）。
 *
 * 直接把 MediaStore 的 content:// uri 透传给系统，靠 FLAG_GRANT_READ_URI_PERMISSION
 * 给接收方临时读权限，不需要像 ShareUtil 那样把文件复制到 cacheDir。
 *
 * 系统会弹出"用以下方式打开"选择器，可选相册、图库、文件管理器等支持图片查看的应用。
 */
object OpenExternalUtil {

    fun open(context: Context, item: ImageItem) {
        runCatching {
            val uri = Uri.parse(item.uri)
            val mime = item.mimeType.ifBlank { "image/*" }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "用其他应用打开"))
        }
    }
}
