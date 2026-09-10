package com.meme.finder.data.ocr

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云端 OCR 占位实现。v0.6 起接入具体云服务（百度/腾讯/阿里等）。
 * 当前 v0.2 阶段：识别失败、什么都不做。
 */
@Singleton
class CloudOcrEngine @Inject constructor() : OcrEngine {
    override val name: String = "cloud"
    override val requiresNetwork: Boolean = true

    override suspend fun recognize(uri: Uri): String? = null
}
