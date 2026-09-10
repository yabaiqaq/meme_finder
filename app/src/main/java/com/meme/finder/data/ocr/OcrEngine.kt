package com.meme.finder.data.ocr

import android.net.Uri

/**
 * OCR 引擎抽象。端侧 ML Kit 与云端实现各实现一次。
 */
interface OcrEngine {
    /** 返回引擎名（用于日志/调试）。 */
    val name: String

    /** 是否需要联网。云端为 true。 */
    val requiresNetwork: Boolean

    /** 对单张图片做 OCR，返回拼接后的全文（保留换行）。失败返回 null。 */
    suspend fun recognize(uri: Uri): String?
}
