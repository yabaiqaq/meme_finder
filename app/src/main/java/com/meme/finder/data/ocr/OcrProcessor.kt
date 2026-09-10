package com.meme.finder.data.ocr

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR 处理器：决定先端侧还是云端。
 * v0.2 策略：直接走端侧 ML Kit；失败回退到云端（v0.6 起云端有真实实现）。
 */
@Singleton
class OcrProcessor @Inject constructor(
    private val mlKit: MlKitOcrEngine,
    private val cloud: CloudOcrEngine,
) {

    /**
     * @return 成功识别到的文字；若两边都失败则返回 null。
     */
    suspend fun recognize(uri: Uri): String? {
        mlKit.recognize(uri)?.let { return it }
        return cloud.recognize(uri)
    }
}
