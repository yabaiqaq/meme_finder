package com.meme.finder.data.ocr

import android.net.Uri
import com.meme.finder.data.ocr.cloud.CloudOcrConfigStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR 处理器：端侧优先 + 云端兜底。
 *
 * 策略：
 * 1. 先用端侧 ML Kit（离线、隐私安全、单图几百毫秒）
 * 2. 若端侧返回空（识别失败/无文字）且云端已配置 -> 调用云端
 * 3. 否则返回端侧结果
 */
@Singleton
class OcrProcessor @Inject constructor(
    private val mlKit: MlKitOcrEngine,
    private val cloud: CloudOcrEngine,
    private val cloudConfig: CloudOcrConfigStore,
) {

    suspend fun recognize(uri: Uri): String? {
        val onDevice = mlKit.recognize(uri)
        if (!onDevice.isNullOrBlank()) return onDevice
        // 端侧没拿到 -> 走云端（如果配置了）
        if (cloudConfig.isConfigured()) {
            return cloud.recognize(uri)
        }
        return onDevice
    }
}
