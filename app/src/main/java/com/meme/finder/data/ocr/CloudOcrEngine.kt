package com.meme.finder.data.ocr

import android.net.Uri
import com.meme.finder.data.ocr.cloud.BaiduCloudOcrEngine
import com.meme.finder.data.ocr.cloud.CloudOcrConfigStore
import com.meme.finder.data.ocr.cloud.CloudOcrProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云端 OCR 分发器。按 [CloudOcrConfigStore] 配置选择具体实现。
 * - 当前仅支持 [CloudOcrProvider.BAIDU]
 * - 其它 provider（腾讯/阿里）后续扩展
 */
@Singleton
class CloudOcrEngine @Inject constructor(
    private val configStore: CloudOcrConfigStore,
    private val baidu: BaiduCloudOcrEngine,
) : OcrEngine {

    override val name: String = "cloud"
    override val requiresNetwork: Boolean = true

    override suspend fun recognize(uri: Uri): String? {
        val config = configStore.load()
        return when (config.provider) {
            CloudOcrProvider.BAIDU -> baidu.recognize(uri)
            else -> null
        }
    }
}
